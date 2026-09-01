package io.geoshift.app.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class IpWhoIsGeoIpProvider : GeoIpProvider {
    override fun lookupCurrentExit(): GeoIpResult {
        val connection = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "GeoShift/0.1")
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("GeoIP HTTP ${connection.responseCode}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            if (!json.optBoolean("success", false)) {
                error(json.optString("message", "GeoIP lookup failed"))
            }
            val timezone = json.optJSONObject("timezone")
            val timezoneId = timezone?.optString("id")
                ?.takeIf { it.isNotBlank() }
                ?: timezone?.optString("time_zone").orEmpty()

            return GeoIpResult(
                ip = json.optString("ip"),
                countryCode = json.optString("country_code").uppercase(),
                region = json.optString("region"),
                city = json.optString("city"),
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
                timezoneId = timezoneId,
            )
        } finally {
            connection.disconnect()
        }
    }
}
