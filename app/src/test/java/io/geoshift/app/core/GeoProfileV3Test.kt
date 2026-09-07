package io.geoshift.app.core

import org.junit.Assert.assertTrue
import org.junit.Test

class GeoProfileV3Test {
    @Test
    fun validRadioIdentityPassesValidation() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            timezoneEnabled = false,
            localeEnabled = false,
            locationEnabled = false,
            wifiEnabled = true,
            wifiSsid = "Example WiFi",
            wifiBssid = "aa:bb:cc:dd:ee:ff",
            telephonyEnabled = true,
            mcc = "310",
            mnc = "260",
            operatorName = "Example Carrier",
            radioSource = "test fixture",
        )

        assertTrue(profile.validate().isEmpty())
    }

    @Test
    fun malformedRadioIdentityIsRejected() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            timezoneEnabled = false,
            localeEnabled = false,
            locationEnabled = false,
            wifiEnabled = true,
            wifiBssid = "not-a-bssid",
            telephonyEnabled = true,
            mcc = "31",
            mnc = "2",
        )

        val errors = profile.validate()
        assertTrue(errors.any { it.startsWith("Wi-Fi BSSID") })
        assertTrue(errors.any { it.startsWith("MCC") })
        assertTrue(errors.any { it.startsWith("MNC") })
    }

    @Test
    fun diagnosticsWarnWhenRadioSourceIsMissing() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            timezoneEnabled = false,
            localeEnabled = false,
            locationEnabled = false,
            geocoderEnabled = false,
            wifiEnabled = true,
            wifiBssid = "aa:bb:cc:dd:ee:ff",
        )

        val issues = ProfileDiagnostics.evaluate(profile)
        assertTrue(issues.any { it.message.contains("no recorded source") })
    }
}
