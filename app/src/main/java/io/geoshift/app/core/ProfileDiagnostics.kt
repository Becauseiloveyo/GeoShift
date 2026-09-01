package io.geoshift.app.core

import java.util.Locale
import java.util.concurrent.TimeUnit

object ProfileDiagnostics {
    enum class Severity { ERROR, WARNING }

    data class Issue(val severity: Severity, val message: String)

    fun evaluate(profile: GeoProfile, nowEpochMs: Long = System.currentTimeMillis()): List<Issue> = buildList {
        profile.validate().forEach { add(Issue(Severity.ERROR, it)) }

        val profileCountry = profile.countryCode.uppercase()
        if (profile.localeEnabled && profile.localeTag.isNotBlank() && profileCountry.isNotBlank()) {
            val localeCountry = Locale.forLanguageTag(profile.localeTag).country.uppercase()
            if (localeCountry.isNotBlank() && localeCountry != profileCountry) {
                add(Issue(Severity.WARNING, "Locale region $localeCountry differs from country $profileCountry"))
            }
        }

        if (profile.locationEnabled && profile.latitude == 0.0 && profile.longitude == 0.0) {
            add(Issue(Severity.WARNING, "Location is 0,0; set a real test location or synchronize from GeoIP"))
        }

        if (profile.followVpn && profile.lastSyncIp.isBlank()) {
            add(Issue(Severity.WARNING, "Follow VPN is enabled but no successful exit-IP sync is recorded"))
        }

        if (profile.followVpn && profile.lastSyncAtEpochMs > 0L) {
            val age = nowEpochMs - profile.lastSyncAtEpochMs
            if (age > TimeUnit.HOURS.toMillis(24)) {
                add(Issue(Severity.WARNING, "Last exit-IP sync is older than 24 hours"))
            }
        }
    }
}
