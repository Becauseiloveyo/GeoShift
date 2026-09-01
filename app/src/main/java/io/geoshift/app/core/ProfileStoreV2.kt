package io.geoshift.app.core

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * Multi-profile storage layered on the same Remote Preferences file used by v0.1.
 * Package names are stable namespaces; new fields default safely for old installs.
 */
object ProfileStoreV2 {
    const val REMOTE_PREFS = ProfileStore.REMOTE_PREFS

    private const val KEY_PROFILE_INDEX = "v2_profile_index"
    private const val PREFIX = "profile::"
    private const val SEPARATOR = "|"

    private fun key(packageName: String, field: String) = "$PREFIX$packageName::$field"
    fun prefixFor(packageName: String): String = "$PREFIX$packageName::"

    fun list(service: XposedService): List<GeoProfile> {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return packageNames(prefs).mapNotNull { load(prefs, it) }
    }

    fun listPackages(service: XposedService): List<String> {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return packageNames(prefs)
    }

    fun load(service: XposedService, packageName: String): GeoProfile? {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return load(prefs, packageName)
    }

    fun load(prefs: SharedPreferences, packageName: String): GeoProfile? {
        if (packageName.isBlank()) return null
        val storedTarget = prefs.getString(key(packageName, ProfileStore.KEY_TARGET_PACKAGE), null)
        if (storedTarget == null) {
            val legacyTarget = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
            return if (legacyTarget == packageName) ProfileStore.load(prefs) else null
        }
        return GeoProfile(
            enabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_ENABLED), true),
            targetPackage = storedTarget,
            followVpn = prefs.getBoolean(key(packageName, ProfileStore.KEY_FOLLOW_VPN), false),
            timezoneEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_TIMEZONE_ENABLED), true),
            timezoneId = prefs.getString(key(packageName, ProfileStore.KEY_TIMEZONE), java.util.TimeZone.getDefault().id).orEmpty(),
            localeEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_LOCALE_ENABLED), true),
            localeTag = prefs.getString(key(packageName, ProfileStore.KEY_LOCALE), java.util.Locale.getDefault().toLanguageTag()).orEmpty(),
            countryCode = prefs.getString(key(packageName, ProfileStore.KEY_COUNTRY), java.util.Locale.getDefault().country).orEmpty(),
            locationEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_LOCATION_ENABLED), true),
            latitude = prefs.getString(key(packageName, ProfileStore.KEY_LATITUDE), "0.0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString(key(packageName, ProfileStore.KEY_LONGITUDE), "0.0")?.toDoubleOrNull() ?: 0.0,
            geocoderEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_GEOCODER_ENABLED), true),
            wifiEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_WIFI_ENABLED), false),
            wifiSsid = prefs.getString(key(packageName, ProfileStore.KEY_WIFI_SSID), "").orEmpty(),
            wifiBssid = prefs.getString(key(packageName, ProfileStore.KEY_WIFI_BSSID), "").orEmpty(),
            telephonyEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_TELEPHONY_ENABLED), false),
            mcc = prefs.getString(key(packageName, ProfileStore.KEY_MCC), "").orEmpty(),
            mnc = prefs.getString(key(packageName, ProfileStore.KEY_MNC), "").orEmpty(),
            operatorName = prefs.getString(key(packageName, ProfileStore.KEY_OPERATOR_NAME), "").orEmpty(),
            radioSource = prefs.getString(key(packageName, ProfileStore.KEY_RADIO_SOURCE), "").orEmpty(),
            lastSyncIp = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_IP), "").orEmpty(),
            lastSyncCity = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_CITY), "").orEmpty(),
            lastSyncRegion = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_REGION), "").orEmpty(),
            lastSyncAtEpochMs = prefs.getLong(key(packageName, ProfileStore.KEY_LAST_SYNC_AT), 0L),
        )
    }

    fun save(service: XposedService, profile: GeoProfile): Boolean {
        if (profile.targetPackage.isBlank()) return false
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return save(prefs, profile)
    }

    fun save(prefs: SharedPreferences, profile: GeoProfile): Boolean {
        if (profile.targetPackage.isBlank()) return false
        val packageName = profile.targetPackage
        val editor = prefs.edit() ?: return false
        val packages = (packageNames(prefs) + packageName).distinct().sorted()
        editor
            .putString(KEY_PROFILE_INDEX, packages.joinToString(SEPARATOR))
            .putBoolean(key(packageName, ProfileStore.KEY_ENABLED), profile.enabled)
            .putString(key(packageName, ProfileStore.KEY_TARGET_PACKAGE), packageName)
            .putBoolean(key(packageName, ProfileStore.KEY_FOLLOW_VPN), profile.followVpn)
            .putBoolean(key(packageName, ProfileStore.KEY_TIMEZONE_ENABLED), profile.timezoneEnabled)
            .putString(key(packageName, ProfileStore.KEY_TIMEZONE), profile.timezoneId)
            .putBoolean(key(packageName, ProfileStore.KEY_LOCALE_ENABLED), profile.localeEnabled)
            .putString(key(packageName, ProfileStore.KEY_LOCALE), profile.localeTag)
            .putString(key(packageName, ProfileStore.KEY_COUNTRY), profile.countryCode.uppercase())
            .putBoolean(key(packageName, ProfileStore.KEY_LOCATION_ENABLED), profile.locationEnabled)
            .putString(key(packageName, ProfileStore.KEY_LATITUDE), profile.latitude.toString())
            .putString(key(packageName, ProfileStore.KEY_LONGITUDE), profile.longitude.toString())
            .putBoolean(key(packageName, ProfileStore.KEY_GEOCODER_ENABLED), profile.geocoderEnabled)
            .putBoolean(key(packageName, ProfileStore.KEY_WIFI_ENABLED), profile.wifiEnabled)
            .putString(key(packageName, ProfileStore.KEY_WIFI_SSID), profile.wifiSsid)
            .putString(key(packageName, ProfileStore.KEY_WIFI_BSSID), profile.wifiBssid.lowercase())
            .putBoolean(key(packageName, ProfileStore.KEY_TELEPHONY_ENABLED), profile.telephonyEnabled)
            .putString(key(packageName, ProfileStore.KEY_MCC), profile.mcc)
            .putString(key(packageName, ProfileStore.KEY_MNC), profile.mnc)
            .putString(key(packageName, ProfileStore.KEY_OPERATOR_NAME), profile.operatorName)
            .putString(key(packageName, ProfileStore.KEY_RADIO_SOURCE), profile.radioSource)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_IP), profile.lastSyncIp)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_CITY), profile.lastSyncCity)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_REGION), profile.lastSyncRegion)
            .putLong(key(packageName, ProfileStore.KEY_LAST_SYNC_AT), profile.lastSyncAtEpochMs)
            .apply()
        return true
    }

    fun delete(service: XposedService, packageName: String): Boolean {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        val editor = prefs.edit() ?: return false
        val keys = listOf(
            ProfileStore.KEY_ENABLED, ProfileStore.KEY_TARGET_PACKAGE, ProfileStore.KEY_FOLLOW_VPN,
            ProfileStore.KEY_TIMEZONE_ENABLED, ProfileStore.KEY_TIMEZONE,
            ProfileStore.KEY_LOCALE_ENABLED, ProfileStore.KEY_LOCALE, ProfileStore.KEY_COUNTRY,
            ProfileStore.KEY_LOCATION_ENABLED, ProfileStore.KEY_LATITUDE, ProfileStore.KEY_LONGITUDE,
            ProfileStore.KEY_GEOCODER_ENABLED,
            ProfileStore.KEY_WIFI_ENABLED, ProfileStore.KEY_WIFI_SSID, ProfileStore.KEY_WIFI_BSSID,
            ProfileStore.KEY_TELEPHONY_ENABLED, ProfileStore.KEY_MCC, ProfileStore.KEY_MNC,
            ProfileStore.KEY_OPERATOR_NAME, ProfileStore.KEY_RADIO_SOURCE,
            ProfileStore.KEY_LAST_SYNC_IP, ProfileStore.KEY_LAST_SYNC_CITY,
            ProfileStore.KEY_LAST_SYNC_REGION, ProfileStore.KEY_LAST_SYNC_AT,
        )
        keys.forEach { editor.remove(key(packageName, it)) }
        val packages = packageNames(prefs).filterNot { it == packageName }
        editor.putString(KEY_PROFILE_INDEX, packages.joinToString(SEPARATOR)).apply()
        return true
    }

    private fun packageNames(prefs: SharedPreferences): List<String> =
        prefs.getString(KEY_PROFILE_INDEX, "").orEmpty()
            .split(SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun migrateLegacyIfNeeded(prefs: SharedPreferences) {
        if (packageNames(prefs).isNotEmpty()) return
        val legacyTarget = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
        if (legacyTarget.isBlank()) return
        save(prefs, ProfileStore.load(prefs))
    }
}
