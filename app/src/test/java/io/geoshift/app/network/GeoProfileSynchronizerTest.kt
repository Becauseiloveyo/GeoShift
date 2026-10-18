package io.geoshift.app.network

import io.geoshift.app.core.GeoProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoProfileSynchronizerTest {
    private val losAngeles = GeoIpResult(
        ip = "203.0.113.10",
        countryCode = "US",
        region = "California",
        city = "Los Angeles",
        latitude = 34.0522,
        longitude = -118.2437,
        timezoneId = "America/Los_Angeles",
    )

    @Test
    fun providedGeoResultCanBeReusedAcrossProfiles() {
        val synchronizer = GeoProfileSynchronizer(GeoIpProvider { error("Provider should not be called") })
        val first = synchronizer.synchronize(
            GeoProfile(targetPackage = "com.example.one"),
            losAngeles,
            nowEpochMs = 1234L,
        )
        val second = synchronizer.synchronize(
            GeoProfile(targetPackage = "com.example.two"),
            losAngeles,
            nowEpochMs = 1234L,
        )

        assertEquals("US", first.profile.countryCode)
        assertEquals("America/Los_Angeles", first.profile.timezoneId)
        assertEquals(34.0522, first.profile.latitude, 0.00001)
        assertEquals(-118.2437, second.profile.longitude, 0.00001)
        assertEquals(1234L, second.profile.lastSyncAtEpochMs)
    }

    @Test
    fun newCountryMakesOldRadioStaleWithoutDeletingValues() {
        val base = GeoProfile(
            targetPackage = "com.example.one", countryCode = "JP",
            latitude = 35.6762, longitude = 139.6503, geoRevision = 4L,
            radioGeoRevision = 4L, wifiEnabled = true,
            wifiSsid = "old-lab", wifiBssid = "02:11:22:33:44:55",
            telephonyEnabled = true, mcc = "440", mnc = "10",
        )
        val result = GeoProfileSynchronizer().synchronize(base, losAngeles, 1234L).profile
        assertEquals(5L, result.geoRevision)
        assertEquals(4L, result.radioGeoRevision)
        assertEquals(true, result.radioStale)
        assertEquals("old-lab", result.wifiSsid)
        assertEquals("440", result.mcc)
        assertEquals("geoip", result.geoSource)
    }

    @Test
    fun lookupFailureLeavesPreviousSnapshotUntouched() {
        val original = GeoProfile(targetPackage = "com.example.one", revision = 7L)
        val failed = GeoProfileSynchronizer(GeoIpProvider { error("upstream down") })
        val outcome = runCatching { failed.synchronize(original) }
        assertEquals(true, outcome.isFailure)
        assertEquals(7L, original.revision)
        assertEquals(0L, original.lastSyncAtEpochMs)
    }

    @Test
    fun resolveCurrentExitCallsProviderOnce() {
        var calls = 0
        val synchronizer = GeoProfileSynchronizer(GeoIpProvider {
            calls++
            losAngeles
        })

        val resolved = synchronizer.resolveCurrentExit()
        synchronizer.synchronize(GeoProfile(targetPackage = "com.example.one"), resolved)
        synchronizer.synchronize(GeoProfile(targetPackage = "com.example.two"), resolved)

        assertEquals(1, calls)
    }
}
