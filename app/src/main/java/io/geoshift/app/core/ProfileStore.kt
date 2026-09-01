package io.geoshift.app.core

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

object ProfileStore {
    const val REMOTE_PREFS = "geo_profile"

    const val KEY_ENABLED = "enabled"
    const val KEY_TARGET_PACKAGE = "target_package"
    const val KEY_FOLLOW_VPN = "follow_vpn"
    const val KEY_TIMEZONE_ENABLED = "timezone_enabled"
    const val KEY_TIMEZONE = "timezone"
    const val KEY_LOCALE_ENABLED = "locale_enabled"
    const val KEY_LOCALE = "locale"
    const val KEY_COUNTRY = "country"
    const val KEY_LOCATION_ENABLED = "location_enabled"
    const val KEY_LATITUDE = "latitude"
    const val KEY_LONGITUDE = "longitude"

    fun load(service: XposedService): GeoProfile = load(service.getRemotePreferences(REMOTE_PREFS))

    fun load(prefs: SharedPreferences): GeoProfile = GeoProfile(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        targetPackage = prefs.getString(KEY_TARGET_PACKAGE, "").orEmpty(),
        followVpn = prefs.getBoolean(KEY_FOLLOW_VPN, false),
        timezoneEnabled = prefs.getBoolean(KEY_TIMEZONE_ENABLED, true),
        timezoneId = prefs.getString(KEY_TIMEZONE, java.util.TimeZone.getDefault().id).orEmpty(),
        localeEnabled = prefs.getBoolean(KEY_LOCALE_ENABLED, true),
        localeTag = prefs.getString(KEY_LOCALE, java.util.Locale.getDefault().toLanguageTag()).orEmpty(),
        countryCode = prefs.getString(KEY_COUNTRY, java.util.Locale.getDefault().country).orEmpty(),
        locationEnabled = prefs.getBoolean(KEY_LOCATION_ENABLED, true),
        latitude = prefs.getString(KEY_LATITUDE, "0.0")?.toDoubleOrNull() ?: 0.0,
        longitude = prefs.getString(KEY_LONGITUDE, "0.0")?.toDoubleOrNull() ?: 0.0,
    )

    fun save(service: XposedService, profile: GeoProfile): Boolean {
        val editor = service.getRemotePreferences(REMOTE_PREFS).edit() ?: return false
        editor
            .putBoolean(KEY_ENABLED, profile.enabled)
            .putString(KEY_TARGET_PACKAGE, profile.targetPackage)
            .putBoolean(KEY_FOLLOW_VPN, profile.followVpn)
            .putBoolean(KEY_TIMEZONE_ENABLED, profile.timezoneEnabled)
            .putString(KEY_TIMEZONE, profile.timezoneId)
            .putBoolean(KEY_LOCALE_ENABLED, profile.localeEnabled)
            .putString(KEY_LOCALE, profile.localeTag)
            .putString(KEY_COUNTRY, profile.countryCode.uppercase())
            .putBoolean(KEY_LOCATION_ENABLED, profile.locationEnabled)
            .putString(KEY_LATITUDE, profile.latitude.toString())
            .putString(KEY_LONGITUDE, profile.longitude.toString())
            .apply()
        return true
    }
}
