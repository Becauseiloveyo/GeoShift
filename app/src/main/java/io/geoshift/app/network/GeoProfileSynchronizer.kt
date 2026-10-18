package io.geoshift.app.network

import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.GeoContextPolicy
import java.util.Locale

class GeoProfileSynchronizer(
    private val geoIpProvider: GeoIpProvider = IpWhoIsGeoIpProvider(),
) {
    data class Outcome(val profile: GeoProfile, val geoIp: GeoIpResult)

    fun resolveCurrentExit(): GeoIpResult = geoIpProvider.lookupCurrentExit()

    fun synchronize(base: GeoProfile, nowEpochMs: Long = System.currentTimeMillis()): Outcome =
        synchronize(base, resolveCurrentExit(), nowEpochMs)

    fun synchronize(
        base: GeoProfile,
        result: GeoIpResult,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Outcome {
        val country = result.countryCode.uppercase()
        val updated = GeoContextPolicy.changedLocation(base, base.copy(
            timezoneId = result.timezoneId.ifBlank { base.timezoneId },
            localeTag = defaultLocaleForCountry(country),
            countryCode = country,
            latitude = result.latitude,
            longitude = result.longitude,
            lastSyncIp = result.ip,
            lastSyncCity = result.city,
            lastSyncRegion = result.region,
            lastSyncAtEpochMs = nowEpochMs,
            geoSource = "geoip",
        ))
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
