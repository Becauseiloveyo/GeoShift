package io.geoshift.app.core

import org.json.JSONArray
import org.json.JSONObject

data class WifiAccessPointProfile(
    val ssid: String = "",
    val bssid: String = "",
    val rssiDbm: Int = -55,
    val frequencyMhz: Int = 5200,
) {
    fun isValid(): Boolean =
        bssid.matches(BSSID_PATTERN) && rssiDbm in -127..0 && frequencyMhz in 2400..7125

    companion object {
        private val BSSID_PATTERN = Regex("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
    }
}

object WifiAccessPointCodec {
    private const val MAX_POINTS = 8

    fun encode(points: List<WifiAccessPointProfile>): String = JSONArray().apply {
        points.asSequence()
            .filter { it.isValid() }
            .distinctBy { it.bssid.lowercase() }
            .take(MAX_POINTS)
            .forEach { point ->
                put(JSONObject().apply {
                    put("ssid", point.ssid)
                    put("bssid", point.bssid.lowercase())
                    put("rssiDbm", point.rssiDbm)
                    put("frequencyMhz", point.frequencyMhz)
                })
            }
    }.toString()

    fun decode(text: String?): List<WifiAccessPointProfile> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            buildList {
                for (index in 0 until minOf(array.length(), MAX_POINTS)) {
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
        }.getOrDefault(emptyList())
    }
}

fun GeoProfile.effectiveWifiAccessPoints(): List<WifiAccessPointProfile> {
    if (!wifiEnabled) return emptyList()
    val primary = if (wifiBssid.isNotBlank()) {
        WifiAccessPointProfile(
            ssid = wifiSsid,
            bssid = wifiBssid.lowercase(),
            rssiDbm = wifiRssiDbm,
            frequencyMhz = 5200,
        ).takeIf { it.isValid() }
    } else null
    return buildList {
        primary?.let(::add)
        addAll(wifiAccessPoints)
    }.distinctBy { it.bssid.lowercase() }.take(8)
}
