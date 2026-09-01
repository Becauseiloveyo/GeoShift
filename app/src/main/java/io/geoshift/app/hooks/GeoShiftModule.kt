package io.geoshift.app.hooks

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.wifi.WifiInfo
import android.os.LocaleList
import android.telephony.TelephonyManager
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileStoreV2
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class GeoShiftModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShift"
        private val TIMEZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
    }

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
        installGeocoderHooks(profile)
        installWifiHooks(profile)
        installTelephonyHooks(profile)
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
        }.onFailure { log(Log.WARN, TAG, "Location hook failed", it) }
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
