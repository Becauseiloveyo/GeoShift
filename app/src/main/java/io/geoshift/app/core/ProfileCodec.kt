package io.geoshift.app.core

import org.json.JSONObject

object ProfileCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(profile: GeoProfile): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("profile", JSONObject().apply {
            put("enabled", profile.enabled)
            put("targetPackage", profile.targetPackage)
            put("followVpn", profile.followVpn)
            put("timezoneEnabled", profile.timezoneEnabled)
            put("timezoneId", profile.timezoneId)
            put("localeEnabled", profile.localeEnabled)
            put("localeTag", profile.localeTag)
            put("countryCode", profile.countryCode)
            put("locationEnabled", profile.locationEnabled)
            put("latitude", profile.latitude)
            put("longitude", profile.longitude)
            put("lastSyncIp", profile.lastSyncIp)
            put("lastSyncCity", profile.lastSyncCity)
            put("lastSyncRegion", profile.lastSyncRegion)
            put("lastSyncAtEpochMs", profile.lastSyncAtEpochMs)
        })
    }.toString(2)

    fun decode(text: String): GeoProfile {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        require(version == SCHEMA_VERSION) { "Unsupported GeoShift profile schema: $version" }
        val json = root.optJSONObject("profile") ?: root
        return GeoProfile(
            enabled = json.optBoolean("enabled", true),
            targetPackage = json.optString("targetPackage", ""),
            followVpn = json.optBoolean("followVpn", false),
            timezoneEnabled = json.optBoolean("timezoneEnabled", true),
            timezoneId = json.optString("timezoneId", java.util.TimeZone.getDefault().id),
            localeEnabled = json.optBoolean("localeEnabled", true),
            localeTag = json.optString("localeTag", java.util.Locale.getDefault().toLanguageTag()),
            countryCode = json.optString("countryCode", java.util.Locale.getDefault().country),
            locationEnabled = json.optBoolean("locationEnabled", true),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            lastSyncIp = json.optString("lastSyncIp", ""),
            lastSyncCity = json.optString("lastSyncCity", ""),
            lastSyncRegion = json.optString("lastSyncRegion", ""),
            lastSyncAtEpochMs = json.optLong("lastSyncAtEpochMs", 0L),
        )
    }
}
