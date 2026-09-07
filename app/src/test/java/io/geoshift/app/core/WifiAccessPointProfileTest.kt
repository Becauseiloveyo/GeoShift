package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAccessPointProfileTest {
    @Test
    fun codecRoundTripKeepsValidDistinctAccessPoints() {
        val points = listOf(
            WifiAccessPointProfile("One", "aa:bb:cc:dd:ee:01", -48, 5200),
            WifiAccessPointProfile("Two", "aa:bb:cc:dd:ee:02", -61, 2412),
            WifiAccessPointProfile("Duplicate", "AA:BB:CC:DD:EE:01", -70, 5200),
        )

        val decoded = WifiAccessPointCodec.decode(WifiAccessPointCodec.encode(points))

        assertEquals(2, decoded.size)
        assertEquals("One", decoded.first().ssid)
        assertEquals("aa:bb:cc:dd:ee:01", decoded.first().bssid)
    }

    @Test
    fun effectiveListKeepsPrimaryFirstAndDeduplicates() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            wifiEnabled = true,
            wifiSsid = "Primary",
            wifiBssid = "aa:bb:cc:dd:ee:01",
            wifiRssiDbm = -42,
            wifiAccessPoints = listOf(
                WifiAccessPointProfile("Duplicate", "aa:bb:cc:dd:ee:01", -60, 5200),
                WifiAccessPointProfile("Neighbor", "aa:bb:cc:dd:ee:02", -65, 2412),
            ),
        )

        val effective = profile.effectiveWifiAccessPoints()

        assertEquals(2, effective.size)
        assertEquals("Primary", effective[0].ssid)
        assertEquals("Neighbor", effective[1].ssid)
    }

    @Test
    fun invalidAccessPointIsRejectedByProfileValidation() {
        val profile = GeoProfile(
            targetPackage = "com.example.app",
            wifiEnabled = true,
            wifiAccessPoints = listOf(
                WifiAccessPointProfile("Bad", "not-a-mac", -55, 5200),
            ),
        )

        assertTrue(profile.validate().any { it.startsWith("Wi-Fi access-point list") })
    }
}
