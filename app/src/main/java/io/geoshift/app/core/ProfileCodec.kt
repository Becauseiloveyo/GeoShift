package io.geoshift.app.core

import org.json.JSONArray
import org.json.JSONObject

object ProfileCodec {
    private const val SCHEMA_VERSION = 4

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
            put("geocoderEnabled", profile.geocoderEnabled)
            put("wifiEnabled", profile.wifiEnabled)
            put("wifiSsid", profile.wifiSsid)
            put("wifiBssid", profile.wifiBssid)
            put("wifiRssiDbm", profile.wifiRssiDbm)
            put("wifiAccessPoints", JSONArray().apply {
                profile.wifiAccessPoints
                    .asSequence()
                    .filter { it.isValid() }
                    .distinctBy { it.bssid.lowercase() }
                    .take(GeoProfile.MAX_WIFI_ACCESS_POINTS)
                    .forEach { point ->
                        put(JSONObject().apply {
                            put("ssid", point.ssid)
                            put("bssid", point.bssid.lowercase())
                            put("rssiDbm", point.rssiDbm)
                            put("frequencyMhz", point.frequencyMhz)
                        })
                    }
            })
            put("telephonyEnabled", profile.telephonyEnabled)
            put("mcc", profile.mcc)
            put("mnc", profile.mnc)
            put("operatorName", profile.operatorName)
            put("cellRadio", profile.cellRadio)
            put("cellAreaCode", profile.cellAreaCode)
            put("cellId", profile.cellId)
            put("radioSource", profile.radioSource)
            put("lastSyncIp", profile.lastSyncIp)
            put("lastSyncCity", profile.lastSyncCity)
            put("lastSyncRegion", profile.lastSyncRegion)
            put("lastSyncAtEpochMs", profile.lastSyncAtEpochMs)
        })
    }.toString(2)

    fun decode(text: String): GeoProfile {
        val root = JSONObject(text)
        val version = root.optInt("schemaVersion", 1)
        require(version in 1..SCHEMA_VERSION) { "Unsupported GeoShift profile schema: $version" }
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
            geocoderEnabled = json.optBoolean("geocoderEnabled", true),
            wifiEnabled = json.optBoolean("wifiEnabled", false),
            wifiSsid = json.optString("wifiSsid", ""),
            wifiBssid = json.optString("wifiBssid", ""),
            wifiRssiDbm = json.optInt("wifiRssiDbm", -45),
            wifiAccessPoints = decodeAccessPoints(json.optJSONArray("wifiAccessPoints")),
            telephonyEnabled = json.optBoolean("telephonyEnabled", false),
            mcc = json.optString("mcc", ""),
            mnc = json.optString("mnc", ""),
            operatorName = json.optString("operatorName", ""),
            cellRadio = json.optString("cellRadio", ""),
            cellAreaCode = json.optLong("cellAreaCode", -1L),
            cellId = json.optLong("cellId", -1L),
            radioSource = json.optString("radioSource", ""),
            lastSyncIp = json.optString("lastSyncIp", ""),
            lastSyncCity = json.optString("lastSyncCity", ""),
            lastSyncRegion = json.optString("lastSyncRegion", ""),
            lastSyncAtEpochMs = json.optLong("lastSyncAtEpochMs", 0L),
        )
    }

    private fun decodeAccessPoints(array: JSONArray?): List<WifiAccessPointProfile> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), GeoProfile.MAX_WIFI_ACCESS_POINTS)) {
                val item = array.optJSONObject(index) ?: continue
                val point = WifiAccessPointProfile(
                    ssid = item.optString("ssid", ""),
                    bssid = item.optString("bssid", "").lowercase(),
                    rssiDbm = item.optInt("rssiDbm", -55),
                    frequencyMhz = item.optInt("frequencyMhz", 5200),
                )
                if (point.isValid()) add(point)
            }
        }.distinctBy { it.bssid.lowercase() }
    }
}