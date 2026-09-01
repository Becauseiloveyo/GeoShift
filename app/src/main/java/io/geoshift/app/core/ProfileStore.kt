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
    const val KEY_GEOCODER_ENABLED = "geocoder_enabled"
    const val KEY_WIFI_ENABLED = "wifi_enabled"
    const val KEY_WIFI_SSID = "wifi_ssid"
    const val KEY_WIFI_BSSID = "wifi_bssid"
    const val KEY_TELEPHONY_ENABLED = "telephony_enabled"
    const val KEY_MCC = "mcc"
    const val KEY_MNC = "mnc"
    const val KEY_OPERATOR_NAME = "operator_name"
    const val KEY_RADIO_SOURCE = "radio_source"
    const val KEY_LAST_SYNC_IP = "last_sync_ip"
    const val KEY_LAST_SYNC_CITY = "last_sync_city"
    const val KEY_LAST_SYNC_REGION = "last_sync_region"
    const val KEY_LAST_SYNC_AT = "last_sync_at"

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
        geocoderEnabled = prefs.getBoolean(KEY_GEOCODER_ENABLED, true),
        wifiEnabled = prefs.getBoolean(KEY_WIFI_ENABLED, false),
        wifiSsid = prefs.getString(KEY_WIFI_SSID, "").orEmpty(),
        wifiBssid = prefs.getString(KEY_WIFI_BSSID, "").orEmpty(),
        telephonyEnabled = prefs.getBoolean(KEY_TELEPHONY_ENABLED, false),
        mcc = prefs.getString(KEY_MCC, "").orEmpty(),
        mnc = prefs.getString(KEY_MNC, "").orEmpty(),
        operatorName = prefs.getString(KEY_OPERATOR_NAME, "").orEmpty(),
        radioSource = prefs.getString(KEY_RADIO_SOURCE, "").orEmpty(),
        lastSyncIp = prefs.getString(KEY_LAST_SYNC_IP, "").orEmpty(),
        lastSyncCity = prefs.getString(KEY_LAST_SYNC_CITY, "").orEmpty(),
        lastSyncRegion = prefs.getString(KEY_LAST_SYNC_REGION, "").orEmpty(),
        lastSyncAtEpochMs = prefs.getLong(KEY_LAST_SYNC_AT, 0L),
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
            .putBoolean(KEY_GEOCODER_ENABLED, profile.geocoderEnabled)
            .putBoolean(KEY_WIFI_ENABLED, profile.wifiEnabled)
            .putString(KEY_WIFI_SSID, profile.wifiSsid)
            .putString(KEY_WIFI_BSSID, profile.wifiBssid.lowercase())
            .putBoolean(KEY_TELEPHONY_ENABLED, profile.telephonyEnabled)
            .putString(KEY_MCC, profile.mcc)
            .putString(KEY_MNC, profile.mnc)
            .putString(KEY_OPERATOR_NAME, profile.operatorName)
            .putString(KEY_RADIO_SOURCE, profile.radioSource)
            .putString(KEY_LAST_SYNC_IP, profile.lastSyncIp)
            .putString(KEY_LAST_SYNC_CITY, profile.lastSyncCity)
            .putString(KEY_LAST_SYNC_REGION, profile.lastSyncRegion)
            .putLong(KEY_LAST_SYNC_AT, profile.lastSyncAtEpochMs)
            .apply()
        return true
    }
}
