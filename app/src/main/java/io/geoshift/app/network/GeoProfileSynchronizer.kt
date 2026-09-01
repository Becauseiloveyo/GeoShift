package io.geoshift.app.network

import io.geoshift.app.core.GeoProfile
import java.util.Locale

class GeoProfileSynchronizer(
    private val geoIpProvider: GeoIpProvider = IpWhoIsGeoIpProvider(),
) {
    data class Outcome(val profile: GeoProfile, val geoIp: GeoIpResult)

    fun synchronize(base: GeoProfile, nowEpochMs: Long = System.currentTimeMillis()): Outcome {
        val result = geoIpProvider.lookupCurrentExit()
        val country = result.countryCode.uppercase()
        val updated = base.copy(
            timezoneId = result.timezoneId.ifBlank { base.timezoneId },
            localeTag = defaultLocaleForCountry(country),
            countryCode = country,
            latitude = result.latitude,
            longitude = result.longitude,
            lastSyncIp = result.ip,
            lastSyncCity = result.city,
            lastSyncRegion = result.region,
            lastSyncAtEpochMs = nowEpochMs,
        )
        return Outcome(updated, result)
    }

    private fun defaultLocaleForCountry(countryCode: String): String {
        val language = when (countryCode) {
            "CN", "TW", "HK", "MO" -> "zh"
            "JP" -> "ja"
            "KR" -> "ko"
            "DE", "AT" -> "de"
            "FR" -> "fr"
            "ES", "MX" -> "es"
            "IT" -> "it"
            "BR", "PT" -> "pt"
            "RU" -> "ru"
            "NL" -> "nl"
            "PL" -> "pl"
            "TR" -> "tr"
            else -> "en"
        }
        return Locale(language, countryCode).toLanguageTag()
    }
}
