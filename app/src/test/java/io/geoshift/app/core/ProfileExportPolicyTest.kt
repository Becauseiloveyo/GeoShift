package io.geoshift.app.core

import org.junit.Assert.*
import org.junit.Test

class ProfileExportPolicyTest {
    @Test fun exportRedactsObservedNetworkAndRadioIdentifiers() {
        val original = GeoProfile(
            targetPackage = "com.example.lab",
            latitude = 35.0,
            longitude = 139.0,
            lastSyncIp = "198.51.100.11",
            lastSyncRegion = "Example region",
            lastSyncCity = "Example city",
            lastSyncAtEpochMs = 13L,
            wifiEnabled = true,
            wifiSsid = "private-network",
            wifiBssid = "aa:bb:cc:dd:ee:ff",
            telephonyEnabled = true,
            mcc = "310",
            mnc = "260",
            operatorName = "private-operator",
            cellRadio = "lte",
            cellAreaCode = 1234L,
            cellId = 42L,
            radioSource = "private-source",
        )
        val exported = ProfileExportPolicy.sanitized(original)
        assertEquals("", exported.lastSyncIp)
        assertEquals(0L, exported.lastSyncAtEpochMs)
        assertEquals("", exported.wifiSsid)
        assertEquals("", exported.wifiBssid)
        assertEquals("", exported.mcc)
        assertEquals("", exported.mnc)
        assertEquals("", exported.operatorName)
        assertEquals(-1L, exported.cellId)
        assertTrue(exported.wifiAccessPoints.isEmpty())
        assertFalse(exported.wifiEnabled)
        assertFalse(exported.telephonyEnabled)
        // Intentionally preserve the user-configured test location.
        assertEquals(35.0, exported.latitude, 0.0)
        assertEquals(139.0, exported.longitude, 0.0)
        assertEquals("198.51.100.11", original.lastSyncIp)
    }

    @Test fun exportDoesNotAlterOriginalConfigRevisionOrPackage() {
        val original = GeoProfile(targetPackage = "com.example.lab", revision = 5L)
        val exported = ProfileExportPolicy.sanitized(original)
        assertEquals("com.example.lab", exported.targetPackage)
        assertEquals(5L, exported.revision)
        assertEquals(original, GeoProfile(targetPackage = "com.example.lab", revision = 5L))
    }
}
