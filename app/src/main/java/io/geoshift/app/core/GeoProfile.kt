package io.geoshift.app.core

import java.util.Locale
import java.util.TimeZone

data class GeoProfile(
    val enabled: Boolean = true,
    val targetPackage: String = "",
    val followVpn: Boolean = false,
    val timezoneEnabled: Boolean = true,
    val timezoneId: String = TimeZone.getDefault().id,
    val localeEnabled: Boolean = true,
    val localeTag: String = Locale.getDefault().toLanguageTag(),
    val countryCode: String = Locale.getDefault().country,
    val locationEnabled: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastSyncIp: String = "",
    val lastSyncCity: String = "",
    val lastSyncRegion: String = "",
    val lastSyncAtEpochMs: Long = 0L,
) {
    fun validate(): List<String> = buildList {
        if (targetPackage.isBlank()) add("Target package is required")
        if (timezoneEnabled && timezoneId !in AVAILABLE_TIMEZONES) {
            add("Unknown time zone: $timezoneId")
        }
        if (localeEnabled && Locale.forLanguageTag(localeTag).language.isBlank()) {
            add("Invalid locale tag: $localeTag")
        }
        if (countryCode.isNotBlank() && !countryCode.matches(Regex("[A-Za-z]{2}"))) {
            add("Country code must be ISO-3166 alpha-2")
        }
        if (locationEnabled && (!latitude.isFinite() || latitude !in -90.0..90.0)) {
            add("Latitude must be between -90 and 90")
        }
        if (locationEnabled && (!longitude.isFinite() || longitude !in -180.0..180.0)) {
            add("Longitude must be between -180 and 180")
        }
        if (lastSyncAtEpochMs < 0L) add("Last sync timestamp cannot be negative")
    }

    companion object {
        private val AVAILABLE_TIMEZONES = TimeZone.getAvailableIDs().toHashSet()
    }
}
