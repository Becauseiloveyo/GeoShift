package io.geoshift.app.core

import java.nio.charset.StandardCharsets
import java.util.Base64

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

/**
 * Compact Remote Preferences encoding that is usable from both Android and local JVM tests.
 * Profile export remains normal JSON in ProfileCodec; this codec is only for internal storage.
 */
object WifiAccessPointCodec {
    private const val MAX_POINTS = 8
    private const val RECORD_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = "|"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(points: List<WifiAccessPointProfile>): String =
        points.asSequence()
            .filter { it.isValid() }
            .distinctBy { it.bssid.lowercase() }
            .take(MAX_POINTS)
            .joinToString(RECORD_SEPARATOR) { point ->
                listOf(
                    encodeText(point.ssid),
                    point.bssid.lowercase(),
                    point.rssiDbm.toString(),
                    point.frequencyMhz.toString(),
                ).joinToString(FIELD_SEPARATOR)
            }

    fun decode(text: String?): List<WifiAccessPointProfile> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(RECORD_SEPARATOR)
            .asSequence()
            .take(MAX_POINTS)
            .mapNotNull { record ->
                val fields = record.split(FIELD_SEPARATOR)
                if (fields.size != 4) return@mapNotNull null
                val point = WifiAccessPointProfile(
                    ssid = decodeText(fields[0]) ?: return@mapNotNull null,
                    bssid = fields[1].lowercase(),
                    rssiDbm = fields[2].toIntOrNull() ?: return@mapNotNull null,
                    frequencyMhz = fields[3].toIntOrNull() ?: return@mapNotNull null,
                )
                point.takeIf { it.isValid() }
            }
            .distinctBy { it.bssid.lowercase() }
            .toList()
    }

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? = runCatching {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }.getOrNull()
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
    }.distinctBy { it.bssid.lowercase() }.take(GeoProfile.MAX_WIFI_ACCESS_POINTS)
}