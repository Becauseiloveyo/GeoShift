package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileHealthTest {
    @Test
    fun completeGeoAndRadioProfileScoresHighly() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            followVpn = false,
            timezoneEnabled = true,
            timezoneId = "America/Los_Angeles",
            localeEnabled = true,
            localeTag = "en-US",
            countryCode = "US",
            locationEnabled = true,
            latitude = 34.0522,
            longitude = -118.2437,
            geocoderEnabled = true,
            wifiEnabled = true,
            wifiSsid = "Primary",
            wifiBssid = "aa:bb:cc:dd:ee:01",
            wifiRssiDbm = -45,
            wifiAccessPoints = listOf(
                WifiAccessPointProfile("One", "aa:bb:cc:dd:ee:01", -45, 5200),
                WifiAccessPointProfile("Two", "aa:bb:cc:dd:ee:02", -58, 2412),
                WifiAccessPointProfile("Three", "aa:bb:cc:dd:ee:03", -64, 2437),
            ),
            telephonyEnabled = true,
            mcc = "310",
            mnc = "260",
            cellRadio = "lte",
            cellAreaCode = 12345,
            cellId = 67890,
            radioSource = "test",
        )

        val report = ProfileHealth.evaluate(profile)

        assertTrue(report.score >= 90)
        assertTrue(report.mapReady)
        assertTrue(report.wifiReady)
        assertTrue(report.cellularReady)
        assertTrue(report.detailedCellIdentity)
        assertEquals(3, report.wifiAccessPointCount)
    }

    @Test
    fun emptyEnabledWifiEnvironmentReducesReadiness() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            wifiEnabled = true,
            locationEnabled = false,
            geocoderEnabled = false,
            timezoneEnabled = false,
            localeEnabled = false,
        )

        val report = ProfileHealth.evaluate(profile)

        assertFalse(report.wifiReady)
        assertTrue(report.score < 100)
    }
}
