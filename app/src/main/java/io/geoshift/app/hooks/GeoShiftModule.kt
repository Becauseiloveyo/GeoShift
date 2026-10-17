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
import io.geoshift.app.core.LocationCallbackPolicy
import io.geoshift.app.core.LocationListenerRegistry
import io.geoshift.app.core.AuditOutcome
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.effectiveWifiAccessPoints
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.abs

class GeoShiftModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShift"
        private val TIMEZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
    }

    private var audit: HookAuditEmitter? = null

    private val locationListenerWrappers = LocationListenerRegistry<LocationListener>()

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

        audit = HookAuditEmitter(this, param.packageName)
        log(Log.INFO, TAG, "Installing reactive profile hooks for ${param.packageName}")
        installTimeZoneHooks(profile)
        installLocaleHooks(profile)
        installLocationHooks(profile)
        installLocationManagerHooks(profile)
        installSdkLocationHooks(param.classLoader, profile)
        installGeocoderHooks(profile)
        installAsyncGeocoderHook(profile)
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
        }.onSuccess {
            audit?.emit("timezone", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("timezone", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Time zone hook failed", it)
        }
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
        }.onSuccess {
            audit?.emit("locale", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("locale", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Locale hook failed", it)
        }
    }

    private fun installLocationHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            hook(Location::class.java.getDeclaredMethod("getLatitude")).intercept { chain ->
                audit?.emit("location_getter", AuditOutcome.HIT, identityVerified = true)
                val current = profile.get()
                if (!current.isLocationActive() || current!!.latitude !in -90.0..90.0) chain.proceed()
                else {
                    audit?.emit("location_getter", AuditOutcome.REWRITTEN, identityVerified = true)
                    current.latitude
                }
            }
            hook(Location::class.java.getDeclaredMethod("getLongitude")).intercept { chain ->
                audit?.emit("location_getter", AuditOutcome.HIT, identityVerified = true)
                val current = profile.get()
                if (!current.isLocationActive() || current!!.longitude !in -180.0..180.0) chain.proceed()
                else {
                    audit?.emit("location_getter", AuditOutcome.REWRITTEN, identityVerified = true)
                    current.longitude
                }
            }
        }.onSuccess {
            audit?.emit("location_getter", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("location_getter", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Location getter hook failed", it)
        }
    }

    private fun installLocationManagerHooks(profile: AtomicReference<GeoProfile?>) {
        runCatching {
            LocationManager::class.java.methods
                .filter { it.name == "getLastKnownLocation" && it.returnType == Location::class.java }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        audit?.emit("framework_last", AuditOutcome.HIT, identityVerified = true)
                        val original = chain.proceed() as? Location
                        val current = profile.get()
                        val changed = locationForProfile(original, current)
                        if (changed !== original) {
                            audit?.emit("framework_last", AuditOutcome.REWRITTEN, identityVerified = true)
                        }
                        changed
                    }
                    audit?.emit("framework_last", AuditOutcome.INSTALLED)
                }

            LocationManager::class.java.methods
                .filter { method ->
                    method.name == "getCurrentLocation" &&
                        method.parameterTypes.any { Consumer::class.java.isAssignableFrom(it) }
                }
                .forEach { method ->
                    hook(method).intercept { chain ->
                        audit?.emit("framework_callback", AuditOutcome.HIT, identityVerified = true)
                        if (!profile.get().isLocationActive()) return@intercept chain.proceed()
                        val index = method.parameterTypes.indexOfFirst { Consumer::class.java.isAssignableFrom(it) }
                        val original = chain.args.getOrNull(index) as? Consumer<Any?>
                            ?: return@intercept chain.proceed()
                        val args = chain.args.toTypedArray()
                        // Never replace a null (timeout, cancellation, unavailable provider)
                        // with a fabricated successful location.
                        args[index] = Consumer<Any?> { value ->
                            val rewritten = locationCallbackValue(value, profile.get())
                            if (rewritten !== value) {
                                audit?.emit("framework_callback", AuditOutcome.REWRITTEN, identityVerified = true)
                            }
                            original.accept(rewritten)
                        }
                        // The CancellationSignal and Executor are forwarded unchanged.
                        chain.proceed(args)
                    }
                    audit?.emit("framework_callback", AuditOutcome.INSTALLED)
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
                        val wasRegistered = locationListenerWrappers.lookup(original) != null
                        val wrapper = locationListenerWrappers.getOrCreate(original) {
                            locationListenerWrapper(original, profile, method.name == "requestSingleUpdate")
                        }
                        val args = chain.args.toTypedArray()
                        args[index] = wrapper
                        try {
                            chain.proceed(args)
                        } catch (error: Throwable) {
                            // An unsuccessful first registration cannot leak a stale proxy.
                            if (!wasRegistered) locationListenerWrappers.forgetIfMatches(original, wrapper)
                            throw error
                        }
                    }
                    audit?.emit("framework_callback", AuditOutcome.INSTALLED)
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
                        val wrapper = locationListenerWrappers.lookup(original) ?: original
                        val args = chain.args.toTypedArray()
                        args[index] = wrapper
                        // Retain mapping if the system throws; the caller may retry removal.
                        val result = chain.proceed(args)
                        locationListenerWrappers.forgetIfMatches(original, wrapper)
                        result
                    }
                }
        }.onFailure {
            audit?.emit("framework_last", AuditOutcome.FAILED)
            audit?.emit("framework_callback", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "LocationManager hook failed", it)
        }
    }

    private fun locationListenerWrapper(
        original: LocationListener,
        profile: AtomicReference<GeoProfile?>,
        oneShot: Boolean,
    ): LocationListener {
        // Identity is maintained by the registry. A one-shot listener releases its
        // mapping on first location delivery, including when client code throws.
        var wrapper: LocationListener? = null
        val created = Proxy.newProxyInstance(
            LocationListener::class.java.classLoader,
            arrayOf(LocationListener::class.java),
        ) { _, method, args ->
            val forwarded = args?.map { value ->
                locationCallbackValue(value, profile.get())
            }?.toTypedArray() ?: emptyArray()
            try {
                method.invoke(original, *forwarded)
            } catch (error: InvocationTargetException) {
                throw error.targetException
            } finally {
                if (oneShot && method.name == "onLocationChanged") {
                    wrapper?.let { locationListenerWrappers.forgetIfMatches(original, it) }
                }
            }
        } as LocationListener
        wrapper = created
        return created
    }

    private fun installSdkLocationHooks(
        classLoader: ClassLoader,
        profile: AtomicReference<GeoProfile?>,
    ) {
        runCatching {
            val resultClass = Class.forName("com.google.android.gms.location.LocationResult", false, classLoader)
            resultClass.methods.filter { it.name == "getLastLocation" && it.parameterCount == 0 }.forEach { method ->
                hook(method).intercept { chain ->
                    val original = chain.proceed() as? Location
                    locationForProfile(original, profile.get())
                }
            }
            resultClass.methods.filter { it.name == "getLocations" && it.parameterCount == 0 }.forEach { method ->
                hook(method).intercept { chain ->
                    locationCallbackValue(chain.proceed(), profile.get())
                }
            }
        }.onFailure { log(Log.DEBUG, TAG, "Google LocationResult not present or hook skipped", it) }

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
                if (method.declaringClass == Location::class.java) return@forEach
                hook(method).intercept { chain ->
                    val current = profile.get()
                    if (!current.isLocationActive()) chain.proceed()
                    else if (latitude) current!!.latitude else current!!.longitude
                }
            }
        }.onFailure { log(Log.DEBUG, TAG, "$className not present or hook skipped", it) }
    }

    private fun locationCallbackValue(value: Any?, current: GeoProfile?): Any? {
        if (!current.isLocationActive()) return value
        return when (value) {
            null -> null
            is Location -> locationForProfile(value, current)
            is List<*> -> LocationCallbackPolicy.transformList(value) { item ->
                if (item is Location) locationForProfile(item, current) else item
            }
            else -> value
        }
    }

    private fun locationForProfile(original: Location?, current: GeoProfile?): Location? =
        LocationCallbackPolicy.transform(original) { source ->
            if (!current.isLocationActive()) source
            else Location(source).apply {
                latitude = current!!.latitude
                longitude = current.longitude
                if (!hasAccuracy()) accuracy = 5f
            }
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
                audit?.emit("geocoder", AuditOutcome.HIT, identityVerified = true)
                val current = profile.get()
                if (!current.isGeocoderActive()) return@intercept chain.proceed()
                val args = chain.args
                val latitude = args.getOrNull(0) as? Double ?: return@intercept chain.proceed()
                val longitude = args.getOrNull(1) as? Double ?: return@intercept chain.proceed()
                val maxResults = (args.getOrNull(2) as? Int) ?: 1
                if (!geocoderMatches(current!!, latitude, longitude, maxResults)) {
                    return@intercept chain.proceed()
                }
                audit?.emit("geocoder", AuditOutcome.REWRITTEN, identityVerified = true)
                listOf(addressFor(current))
            }
        }.onSuccess {
            audit?.emit("geocoder", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("geocoder", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Geocoder hook failed", it)
        }
    }

    /**
     * API 33+ asynchronous reverse geocoding: pass through errors, empty successes
     * and invalid requests. Only replace an existing non-empty successful result.
     * The original request is dispatched unchanged, preserving callback timing.
     */
    private fun installAsyncGeocoderHook(profile: AtomicReference<GeoProfile?>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val callbackType = Geocoder.GeocodeListener::class.java
            val method = Geocoder::class.java.getMethod(
                "getFromLocation",
                java.lang.Double.TYPE,
                java.lang.Double.TYPE,
                Integer.TYPE,
                callbackType,
            )
            hook(method).intercept { chain ->
                audit?.emit("geocoder", AuditOutcome.HIT, identityVerified = true)
                val latitude = chain.args.getOrNull(0) as? Double ?: return@intercept chain.proceed()
                val longitude = chain.args.getOrNull(1) as? Double ?: return@intercept chain.proceed()
                val count = chain.args.getOrNull(2) as? Int ?: return@intercept chain.proceed()
                val listener = chain.args.getOrNull(3) ?: return@intercept chain.proceed()
                val requestedProfile = profile.get()
                if (!requestedProfile.isGeocoderActive() ||
                    !geocoderMatches(requestedProfile!!, latitude, longitude, count)
                ) return@intercept chain.proceed()

                val wrapped = runCatching {
                    Proxy.newProxyInstance(
                        listener.javaClass.classLoader,
                        arrayOf(callbackType),
                    ) { _, callbackMethod, callbackArgs ->
                        val result = callbackArgs?.toMutableList() ?: mutableListOf()
                        if (callbackMethod.name == "onGeocode" &&
                            result.firstOrNull() is List<*> &&
                            (result[0] as List<*>).isNotEmpty() &&
                            profile.get() == requestedProfile
                        ) {
                            result[0] = listOf(addressFor(requestedProfile))
                            audit?.emit("geocoder", AuditOutcome.REWRITTEN, identityVerified = true)
                        }
                        try {
                            callbackMethod.invoke(listener, *result.toTypedArray())
                        } catch (error: InvocationTargetException) {
                            throw error.targetException
                        }
                    }
                }.onFailure {
                    audit?.emit("geocoder", AuditOutcome.FAILED)
                    log(Log.WARN, TAG, "Unable to wrap async geocoder listener", it)
                }.getOrNull() ?: return@intercept chain.proceed()

                val forwarded = chain.args.toTypedArray()
                forwarded[3] = wrapped
                chain.proceed(forwarded)
            }
        }.onSuccess {
            audit?.emit("geocoder", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("geocoder", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Async Geocoder hook unavailable", it)
        }
    }

    private fun geocoderMatches(current: GeoProfile, latitude: Double, longitude: Double, count: Int): Boolean =
        count > 0 && latitude.isFinite() && longitude.isFinite() &&
            abs(latitude - current.latitude) <= 0.02 && abs(longitude - current.longitude) <= 0.02

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
                val points = current?.effectiveWifiAccessPoints().orEmpty()
                if (!current.isWifiActive() || points.isEmpty() || Build.VERSION.SDK_INT < 30) {
                    return@intercept chain.proceed()
                }
                points.map { point ->
                    ScanResult().apply {
                        SSID = point.ssid
                        BSSID = point.bssid.lowercase()
                        level = point.rssiDbm
                        frequency = point.frequencyMhz
                        timestamp = SystemClock.elapsedRealtimeNanos() / 1_000L
                    }
                }
            }
        }.onSuccess {
            audit?.emit("wifi", AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit("wifi", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "Wi-Fi scan-result hook failed", it)
        }
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
            }.onSuccess {
                audit?.emit("cell_identity", AuditOutcome.INSTALLED)
            }.onFailure {
                audit?.emit("cell_identity", AuditOutcome.FAILED)
                log(Log.DEBUG, TAG, "${spec.className} hook skipped", it)
            }
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
            val surface = if (target == WifiInfo::class.java) "wifi" else "telephony"
            hook(method).intercept { chain ->
                audit?.emit(surface, AuditOutcome.HIT, identityVerified = true)
                val chosen = value(profile.get())
                if (chosen != null) audit?.emit(surface, AuditOutcome.REWRITTEN, identityVerified = true)
                chosen ?: chain.proceed()
            }
            audit?.emit(surface, AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit(if (target == WifiInfo::class.java) "wifi" else "telephony", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "$label hook failed", it)
        }
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
            val surface = if (target == WifiInfo::class.java) "wifi" else "telephony"
            hook(method).intercept { chain ->
                audit?.emit(surface, AuditOutcome.HIT, identityVerified = true)
                val chosen = value(profile.get())
                if (chosen != null) audit?.emit(surface, AuditOutcome.REWRITTEN, identityVerified = true)
                chosen ?: chain.proceed()
            }
            audit?.emit(surface, AuditOutcome.INSTALLED)
        }.onFailure {
            audit?.emit(if (target == WifiInfo::class.java) "wifi" else "telephony", AuditOutcome.FAILED)
            log(Log.WARN, TAG, "$label hook failed", it)
        }
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