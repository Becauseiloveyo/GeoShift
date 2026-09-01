package io.geoshift.app.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class OpenCellIdRadioEnvironmentProvider(
    private val apiKey: String,
) : RadioEnvironmentProvider {
    override fun nearbyWifi(latitude: Double, longitude: Double, radiusMeters: Int): List<WifiEnvironment> = emptyList()

    override fun nearbyCells(latitude: Double, longitude: Double, radiusMeters: Int): List<CellEnvironment> {
        require(apiKey.isNotBlank()) { "OpenCellID API key is missing" }
        val box = GeoMath.boundingBox(latitude, longitude, radiusMeters.coerceAtMost(1_500))
        val bbox = listOf(box.minLat, box.minLon, box.maxLat, box.maxLon).joinToString(",")
        val url = URL(
            "https://www.opencellid.org/cell/getInArea" +
                "?key=${encode(apiKey)}&BBOX=${encode(bbox)}&limit=50&format=json"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GeoShift/0.2 radio-environment-provider")
        }
        try {
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("OpenCellID HTTP ${connection.responseCode}")
            val json = JSONObject(body)
            if (json.has("error")) error(json.optString("error", "OpenCellID request failed"))
            val cells = json.optJSONArray("cells") ?: return emptyList()
            return buildList {
                for (i in 0 until cells.length()) {
                    val item = cells.optJSONObject(i) ?: continue
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val area = when {
                        item.has("tac") -> item.optLong("tac", 0L)
                        else -> item.optLong("lac", 0L)
                    }
                    add(
                        CellEnvironment(
                            radio = item.optString("radio", "UNKNOWN"),
                            mcc = item.optInt("mcc", 0),
                            mnc = item.optInt("mnc", 0),
                            areaCode = area,
                            cellId = item.optLong("cellid", item.optLong("cid", 0L)),
                            latitude = lat,
                            longitude = lon,
                            estimatedSignalDbm = if (item.has("averageSignalStrength")) item.optInt("averageSignalStrength") else null,
                            source = "OpenCellID",
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
