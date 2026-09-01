package io.geoshift.app.hooks

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileStoreV2
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.time.ZoneId
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.abs

class GeoShiftModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShift"
        private val TIMEZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
    }

    private val locationListenerWrappers = Collections.synchronizedMap(
        WeakHashMap<LocationListener, LocationListener>()
    )

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        val prefs = getRemotePreferences(ProfileStoreV2.REMOTE_PREFS)
        val profile = AtomicReference(ProfileStoreV2.load(prefs, param.packageName))
        val prefix = ProfileStoreV2.prefixFor(param.packageName)
        prefs.registerOnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == null || key.startsWith(prefix)) {
                profile.set(ProfileStoreV2.load(changedPrefs, param.packageName))
            }
        }

        log(Log.INFO, TAG, "Installing reactive profile hooks for ${param.packageName}")
        installTimeZoneHooks(profile)
        installLocaleHooks(profile)
        installLocationHooks(profile)
        installLocationManagerHooks(profile)
        installSdkLocationHooks(param.classLoader, profile)
        installGeocoderHooks(profile)
        installWifiHooks(profile)
        installTelephonyHooks(profile)
        installCellIdentityHooks(profile)
    }

    private fun installTimeZoneHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            hook(TimeZone::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                val current = profile.get()
                if (!current.isTimezoneActive()) chain.proceed() else TimeZone.getTimeZone(current!!.timezoneId)
            }
            hook(ZoneId::class.java.getDeclaredMethod("systemDefault")).intercept { chain ->
                val current = profile.get()
                if (!current.isTimezoneActive()) chain.proceed()
                else runCatching { ZoneId.of(current!!.timezoneId) }.getOrElse { chain.proceed() }
            }
        }.onFailure { log(Log.WARN, TAG, "Time zone hook failed", it) }
    }

    private fun installLocaleHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            hook(Locale::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                localeOrOriginal(profile.get(), chain::proceed)
            }
            hook(Locale::class.java.getDeclaredMethod("getDefault", Locale.Category::class.java)).intercept { chain ->
                localeOrOriginal(profile.get(), chain::proceed)
            }
            hook(LocaleList::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                localeListOrOriginal(profile.get(), chain::proceed)
            }
            hook(LocaleList::class.java.getDeclaredMethod("getAdjustedDefault")).intercept { chain ->
                localeListOrOriginal(profile.get(), chain::proceed)
            }
        }.onFailure { log(Log.WARN, TAG, "Locale hook failed", it) }
    }

    private fun installLocationHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            hook(Location::class.java.getDeclaredMethod("getLatitude")).intercept { chain ->
                val current = profile.get()
                if (!current.isLocationActive() || current!!.latitude !in -90.0..90.0) chain.proceed() else current.latitude
            }
            hook(Location::class.java.getDeclaredMethod("getLongitude")).intercept { chain ->
                val current = profile.get()
                if (!current.isLocationActive() || current!!.longitude !in -180.0..180.0) chain.proceed() else current.longitude
            }
        }.onFailure { log(Log.WARN, TAG, "Location getter hook failed", it) }
    }

    /**
     * Covers the public Android location delivery paths used by map and navigation apps.
     * The listener wrapper is identity-preserving through removeUpdates(), and reads the
     * AtomicReference at callback time so profile edits take effect without re-registering.
     */
    private fun installLocationManagerHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            LocationManager::class.java.methods
                .filter { it.name == "getLastKnownLocation" && it.returnType == Location::class.java }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        val original = chain.proceed() as? Location
                        locationForProfile(original, profile.get(), synthesize = true)
                    }
                }

            LocationManager::class.java.methods
                .filter { method ->
                    method.name == "getCurrentLocation" &&
                        method.parameterTypes.any { Consumer::class.java.isAssignableFrom(it) }
                }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        val current = profile.get()
                        if (!current.isLocationActive()) return@intercept chain.proceed()
                        val index = method.parameterTypes.indexOfFirst { Consumer::class.java.isAssignableFrom(it) }
                        val original = chain.args.getOrNull(index) as? Consumer<Any?>
                            ?: return@intercept chain.proceed()
                        val args = chain.args.toTypedArray()
                        args[index] = Consumer<Any?> { value ->
                            original.accept(locationCallbackValue(value, profile.get(), synthesizeEmpty = true))
                        }
                        chain.proceed(args)
                    }
                }

            LocationManager::class.java.methods
                .filter { method ->
                    (method.name == "requestLocationUpdates" || method.name == "requestSingleUpdate") &&
                        method.parameterTypes.any { LocationListener::class.java.isAssignableFrom(it) }
                }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        if (!profile.get().isLocationActive()) return@intercept chain.proceed()
                        val index = method.parameterTypes.indexOfFirst { LocationListener::class.java.isAssignableFrom(it) }
                        val original = chain.args.getOrNull(index) as? LocationListener
                            ?: return@intercept chain.proceed()
                        val args = chain.args.toTypedArray()
                        args[index] = locationListenerWrapper(original, profile)
                        chain.proceed(args)
                    }
                }

            LocationManager::class.java.methods
                .filter { method ->
                    method.name == "removeUpdates" &&
                        method.parameterTypes.any { LocationListener::class.java.isAssignableFrom(it) }
                }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        val index = method.parameterTypes.indexOfFirst { LocationListener::class.java.isAssignableFrom(it) }
                        val original = chain.args.getOrNull(index) as? LocationListener
                            ?: return@intercept chain.proceed()
                        val args = chain.args.toTypedArray()
                        args[index] = synchronized(locationListenerWrappers) {
                            locationListenerWrappers.remove(original)
                        } ?: original
                        chain.proceed(args)
                    }
                }
        }.onFailure { log(Log.WARN, TAG, "LocationManager hook failed", it) }
    }

    private fun locationListenerWrapper(
        original: LocationListener,
        profile: AtomicReference<GeoProfile?>,
    ): LocationListener {
        synchronized(locationListenerWrappers) {
            locationListenerWrappers[original]?.let { return it }
            val proxy = Proxy.newProxyInstance(
                original.javaClass.classLoader,
                arrayOf(LocationListener::class.java),
            ) { _, method, args ->
                val forwarded = args?.map { value ->
                    locationCallbackValue(value, profile.get(), synthesizeEmpty = false)
                }?.toTypedArray() ?: emptyArray()
                try {
                    method.invoke(original, *forwarded)
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            } as LocationListener
            locationListenerWrappers[original] = proxy
            return proxy
        }
    }

    private fun installSdkLocationHooks(
        classLoader: ClassLoader,
        profile: AtomicReference<GeoProfile?>,
    ) {
        // Google Play services LocationCallback exposes LocationResult to the app.
        runCatching {
            val resultClass = Class.forName("com.google.android.gms.location.LocationResult", false, classLoader)
            resultClass.methods.filter { it.name == "getLastLocation" && it.parameterCount == 0 }.forEach { method ->
                hook(method).intercept { chain ->
                    val original = chain.proceed() as? Location
                    locationForProfile(original, profile.get(), synthesize = true)
                }
            }
            resultClass.methods.filter { it.name == "getLocations" && it.parameterCount == 0 }.forEach { method ->
                hook(method).intercept { chain ->
                    locationCallbackValue(chain.proceed(), profile.get(), synthesizeEmpty = true)
                }
            }
        }.onFailure { log(Log.DEBUG, TAG, "Google LocationResult not present or hook skipped", it) }

        // Common Chinese location SDK result objects used by map/navigation apps.
        hookSdkCoordinateClass(classLoader, "com.amap.api.location.AMapLocation", profile)
        hookSdkCoordinateClass(classLoader, "com.baidu.location.BDLocation", profile)
    }

    private fun hookSdkCoordinateClass(
        classLoader: ClassLoader,
        className: String,
        profile: AtomicReference<GeoProfile?>,
    ) {
        runCatching {
            val clazz = Class.forName(className, false, classLoader)
            listOf("getLatitude" to true, "getLongitude" to false).forEach { (name, latitude) ->
                val method = clazz.getMethod(name)
                // If the SDK inherits android.location.Location without overriding the method,
                // the base Location hook already covers it and must not be installed twice.
                if (method.declaringClass == Location::class.java) return@forEach
                hook(method).intercept { chain ->
                    val current = profile.get()
                    if (!current.isLocationActive()) chain.proceed()
                    else if (latitude) current!!.latitude else current!!.longitude
                }
            }
        }.onFailure { log(Log.DEBUG, TAG, "$className not present or hook skipped", it) }
    }

    private fun locationCallbackValue(value: Any?, current: GeoProfile?, synthesizeEmpty: Boolean): Any? {
        if (!current.isLocationActive()) return value
        return when (value) {
            null -> if (synthesizeEmpty) locationForProfile(null, current, synthesize = true) else null
            is Location -> locationForProfile(value, current, synthesize = true)
            is List<*> -> {
                if (value.isEmpty() && synthesizeEmpty) listOf(locationForProfile(null, current, synthesize = true))
                else value.map { item ->
                    if (item is Location) locationForProfile(item, current, synthesize = true) else item
                }
            }
            else -> value
        }
    }

    private fun locationForProfile(original: Location?, current: GeoProfile?, synthesize: Boolean): Location? {
        if (!current.isLocationActive()) return original
        if (original == null && !synthesize) return null
        val result = original?.let(::Location) ?: Location(LocationManager.GPS_PROVIDER).apply {
            accuracy = 5f
            time = System.currentTimeMillis()
            setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos())
        }
        result.latitude = current!!.latitude
        result.longitude = current.longitude
        if (result.time <= 0L) result.time = System.currentTimeMillis()
        if (result.elapsedRealtimeNanos <= 0L) result.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos())
        if (!result.hasAccuracy()) result.accuracy = 5f
        return result
    }

    private fun installGeocoderHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            val method = Geocoder::class.java.getMethod(
                "getFromLocation",
                java.lang.Double.TYPE,
                java.lang.Double.TYPE,
                Integer.TYPE,
            )
            hook(method).intercept { chain ->
                val current = profile.get()
                if (!current.isGeocoderActive()) return@intercept chain.proceed()
                val args = chain.args
                val latitude = args.getOrNull(0) as? Double ?: return@intercept chain.proceed()
                val longitude = args.getOrNull(1) as? Double ?: return@intercept chain.proceed()
                val maxResults = (args.getOrNull(2) as? Int) ?: 1
                if (maxResults <= 0 || abs(latitude - current!!.latitude) > 0.02 || abs(longitude - current.longitude) > 0.02) {
                    return@intercept chain.proceed()
                }
                listOf(addressFor(current))
            }
        }.onFailure { log(Log.WARN, TAG, "Geocoder hook failed", it) }
    }

    private fun installWifiHooks(profile: AtomicReference<GeoProfile?>) {
        hookStringMethod(WifiInfo::class.java, "getSSID", profile, "Wi-Fi SSID") { current ->
            if (!current.isWifiActive() || current!!.wifiSsid.isBlank()) null
            else "\"${current.wifiSsid.replace("\"", "\\\"")}\""
        }
        hookStringMethod(WifiInfo::class.java, "getBSSID", profile, "Wi-Fi BSSID") { current ->
            if (!current.isWifiActive() || current!!.wifiBssid.isBlank()) null else current.wifiBssid.lowercase()
        }
        hookIntMethod(WifiInfo::class.java, "getRssi", profile, "Wi-Fi RSSI") { current ->
            if (!current.isWifiActive()) null else current!!.wifiRssiDbm
        }

        runCatching {
            val scanMethod = WifiManager::class.java.getMethod("getScanResults")
            hook(scanMethod).intercept { chain ->
                val current = profile.get()
                if (!current.isWifiActive() || current!!.wifiBssid.isBlank() || Build.VERSION.SDK_INT < 30) {
                    return@intercept chain.proceed()
                }
                listOf(
                    ScanResult().apply {
                        SSID = current.wifiSsid
                        BSSID = current.wifiBssid.lowercase()
                        level = current.wifiRssiDbm
                        frequency = 5200
                        timestamp = SystemClock.elapsedRealtimeNanos() / 1_000L
                    }
                )
            }
        }.onFailure { log(Log.WARN, TAG, "Wi-Fi scan-result hook failed", it) }
    }

    private fun installTelephonyHooks(profile: AtomicReference<GeoProfile?>) {
        hookStringMethod(TelephonyManager::class.java, "getNetworkCountryIso", profile, "network country") { current ->
            if (!current.isTelephonyActive() || current!!.countryCode.isBlank()) null else current.countryCode.lowercase()
        }
        hookStringMethod(TelephonyManager::class.java, "getSimCountryIso", profile, "SIM country") { current ->
            if (!current.isTelephonyActive() || current!!.countryCode.isBlank()) null else current.countryCode.lowercase()
        }
        hookStringMethod(TelephonyManager::class.java, "getNetworkOperator", profile, "network operator") { current ->
            if (!current.isTelephonyActive() || current!!.mcc.isBlank() || current.mnc.isBlank()) null else current.mcc + current.mnc
        }
        hookStringMethod(TelephonyManager::class.java, "getSimOperator", profile, "SIM operator") { current ->
            if (!current.isTelephonyActive() || current!!.mcc.isBlank() || current.mnc.isBlank()) null else current.mcc + current.mnc
        }
        hookStringMethod(TelephonyManager::class.java, "getNetworkOperatorName", profile, "network operator name") { current ->
            if (!current.isTelephonyActive() || current!!.operatorName.isBlank()) null else current.operatorName
        }
        hookStringMethod(TelephonyManager::class.java, "getSimOperatorName", profile, "SIM operator name") { current ->
            if (!current.isTelephonyActive() || current!!.operatorName.isBlank()) null else current.operatorName
        }
    }

    private fun installCellIdentityHooks(profile: AtomicReference<GeoProfile?>) {
        data class CellSpec(
            val className: String,
            val radioNames: Set<String>,
            val cellIdMethod: String,
            val cellIdIsLong: Boolean,
            val areaMethod: String,
        )

        val specs = listOf(
            CellSpec("android.telephony.CellIdentityLte", setOf("lte"), "getCi", false, "getTac"),
            CellSpec("android.telephony.CellIdentityNr", setOf("nr", "5g"), "getNci", true, "getTac"),
            CellSpec("android.telephony.CellIdentityGsm", setOf("gsm"), "getCid", false, "getLac"),
            CellSpec("android.telephony.CellIdentityWcdma", setOf("umts", "wcdma"), "getCid", false, "getLac"),
            CellSpec("android.telephony.CellIdentityTdscdma", setOf("tdscdma", "td-scdma"), "getCid", false, "getLac"),
        )

        specs.forEach { spec ->
            runCatching {
                val clazz = Class.forName(spec.className)
                hook(clazz.getMethod("getMccString")).intercept { chain ->
                    val current = profile.get()
                    if (!current.matchesCellSpec(spec.radioNames) || current!!.mcc.isBlank()) chain.proceed() else current.mcc
                }
                hook(clazz.getMethod("getMncString")).intercept { chain ->
                    val current = profile.get()
                    if (!current.matchesCellSpec(spec.radioNames) || current!!.mnc.isBlank()) chain.proceed() else current.mnc
                }
                hook(clazz.getMethod(spec.areaMethod)).intercept { chain ->
                    val current = profile.get()
                    if (!current.matchesCellSpec(spec.radioNames) || current!!.cellAreaCode < 0L) chain.proceed()
                    else current.cellAreaCode.toInt()
                }
                hook(clazz.getMethod(spec.cellIdMethod)).intercept { chain ->
                    val current = profile.get()
                    if (!current.matchesCellSpec(spec.radioNames) || current!!.cellId < 0L) chain.proceed()
                    else if (spec.cellIdIsLong) current.cellId else current.cellId.toInt()
                }
            }.onFailure { log(Log.DEBUG, TAG, "${spec.className} hook skipped", it) }
        }
    }

    private fun hookStringMethod(
        target: Class<*>,
        methodName: String,
        profile: AtomicReference<GeoProfile?>,
        label: String,
        value: (GeoProfile?) -> String?,
    ) {
        runCatching {
            val method = target.getMethod(methodName)
            hook(method).intercept { chain -> value(profile.get()) ?: chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "$label hook failed", it) }
    }

    private fun hookIntMethod(
        target: Class<*>,
        methodName: String,
        profile: AtomicReference<GeoProfile?>,
        label: String,
        value: (GeoProfile?) -> Int?,
    ) {
        runCatching {
            val method = target.getMethod(methodName)
            hook(method).intercept { chain -> value(profile.get()) ?: chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "$label hook failed", it) }
    }

    private fun addressFor(profile: GeoProfile): Address {
        val locale = Locale.forLanguageTag(profile.localeTag).takeIf { it.language.isNotBlank() } ?: Locale.ENGLISH
        return Address(locale).apply {
            latitude = profile.latitude
            longitude = profile.longitude
            countryCode = profile.countryCode.uppercase()
            countryName = Locale("", profile.countryCode.uppercase()).getDisplayCountry(locale)
            adminArea = profile.lastSyncRegion.ifBlank { null }
            locality = profile.lastSyncCity.ifBlank { null }
            featureName = profile.lastSyncCity.ifBlank { profile.countryCode.uppercase() }
        }
    }

    private fun GeoProfile?.isTimezoneActive(): Boolean =
        this != null && enabled && timezoneEnabled && timezoneId in TIMEZONE_IDS

    private fun GeoProfile?.isLocaleActive(): Boolean =
        this != null && enabled && localeEnabled && localeTag.isNotBlank()

    private fun GeoProfile?.isLocationActive(): Boolean =
        this != null && enabled && locationEnabled && latitude.isFinite() && longitude.isFinite()

    private fun GeoProfile?.isGeocoderActive(): Boolean =
        isLocationActive() && this!!.geocoderEnabled

    private fun GeoProfile?.isWifiActive(): Boolean =
        this != null && enabled && wifiEnabled

    private fun GeoProfile?.isTelephonyActive(): Boolean =
        this != null && enabled && telephonyEnabled

    private fun GeoProfile?.matchesCellSpec(radios: Set<String>): Boolean {
        if (!isTelephonyActive()) return false
        val normalized = this!!.cellRadio.trim().lowercase()
        if (normalized.isBlank()) return false
        return radios.any { normalized == it || normalized.contains(it) }
    }

    private fun localeOrOriginal(current: GeoProfile?, original: () -> Any?): Any? {
        if (!current.isLocaleActive()) return original()
        val locale = Locale.forLanguageTag(current!!.localeTag)
        return if (locale.language.isBlank()) original() else locale
    }

    private fun localeListOrOriginal(current: GeoProfile?, original: () -> Any?): Any? {
        if (!current.isLocaleActive()) return original()
        val locale = Locale.forLanguageTag(current!!.localeTag)
        return if (locale.language.isBlank()) original() else LocaleList(locale)
    }
}
