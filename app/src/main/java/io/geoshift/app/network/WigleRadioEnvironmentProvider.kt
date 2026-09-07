package io.geoshift.app.network

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WigleRadioEnvironmentProvider(
    private val tokenName: String,
    private val token: String,
) : RadioEnvironmentProvider {
    override fun nearbyWifi(latitude: Double, longitude: Double, radiusMeters: Int): List<WifiEnvironment> {
        require(tokenName.isNotBlank() && token.isNotBlank()) { "WiGLE API token is missing" }
        val box = GeoMath.boundingBox(latitude, longitude, radiusMeters.coerceAtMost(1_500))
        val params = linkedMapOf(
            "latrange1" to box.minLat.toString(),
            "latrange2" to box.maxLat.toString(),
            "longrange1" to box.minLon.toString(),
            "longrange2" to box.maxLon.toString(),
            "resultsPerPage" to "50",
        )
        val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val connection = (URL("https://api.wigle.net/api/v2/network/search?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GeoShift/0.2 radio-environment-provider")
            val basic = Base64.encodeToString("$tokenName:$token".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            setRequestProperty("Authorization", "Basic $basic")
        }
        try {
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("WiGLE HTTP ${connection.responseCode}")
            val json = JSONObject(body)
            if (!json.optBoolean("success", false)) error(json.optString("message", "WiGLE request failed"))
            val results = json.optJSONArray("results") ?: return emptyList()
            return buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val lat = item.optDouble("trilat", Double.NaN)
                    val lon = item.optDouble("trilong", Double.NaN)
                    val bssid = item.optString("netid", "").uppercase()
                    if (bssid.isBlank() || !lat.isFinite() || !lon.isFinite()) continue
                    add(
                        WifiEnvironment(
                            bssid = bssid,
                            ssid = item.optString("ssid", "").ifBlank { null },
                            latitude = lat,
                            longitude = lon,
                            source = "WiGLE",
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    override fun nearbyCells(latitude: Double, longitude: Double, radiusMeters: Int): List<CellEnvironment> = emptyList()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
