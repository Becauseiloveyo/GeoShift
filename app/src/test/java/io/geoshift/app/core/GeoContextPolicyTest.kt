package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoContextPolicyTest {
    private val first = GeoProfile(
        targetPackage = "com.example.lab",
        countryCode = "US", lastSyncRegion = "California", lastSyncCity = "Los Angeles",
        latitude = 34.0522, longitude = -118.2437, geoRevision = 2L,
        radioGeoRevision = 2L,
        wifiEnabled = true, wifiSsid = "old-lab", wifiBssid = "02:11:22:33:44:55",
        telephonyEnabled = true, mcc = "310", mnc = "260",
    )

    @Test fun crossingCountryMarksOldRadioStaleButRetainsRecoverableValues() {
        val changed = GeoContextPolicy.changedLocation(first, first.copy(
            countryCode = "JP", lastSyncRegion = "Tokyo", lastSyncCity = "Tokyo",
            latitude = 35.6762, longitude = 139.6503,
        ))
        assertEquals(3L, changed.geoRevision)
        assertEquals(2L, changed.radioGeoRevision)
        assertTrue(changed.radioStale)
        assertFalse(changed.isRadioCurrent())
        assertEquals("old-lab", changed.wifiSsid)
        assertEquals("02:11:22:33:44:55", changed.wifiBssid)
        assertEquals("310", changed.mcc)
        assertTrue(changed.effectiveWifiAccessPoints().isEmpty())
        assertFalse(ProfileHealth.evaluate(changed).cellularReady)
    }

    @Test fun sameRegionAndSmallCoordinateJitterPreservesBoundRadio() {
        val next = GeoContextPolicy.changedLocation(first, first.copy(
            latitude = 34.0525, longitude = -118.2438,
        ))
        assertEquals(2L, next.geoRevision)
        assertFalse(next.radioStale)
        assertTrue(next.isRadioCurrent())
        assertEquals(1, next.effectiveWifiAccessPoints().size)
    }

    @Test fun movementAcrossCityEvenWithinSameNamedRegionInvalidatesRadio() {
        val next = GeoContextPolicy.changedLocation(first, first.copy(latitude = 34.07))
        assertTrue(next.radioStale)
        assertEquals(3L, next.geoRevision)
    }

    @Test fun manualConfirmationRestoresDataForCurrentContext() {
        val moved = GeoContextPolicy.changedLocation(first, first.copy(
            countryCode = "JP", lastSyncCity = "Tokyo",
        ))
        val confirmed = moved.confirmRadioForCurrentGeography("manual confirmation")
        assertTrue(confirmed.isRadioCurrent())
        assertEquals(moved.geoRevision, confirmed.radioGeoRevision)
        assertEquals("manual confirmation", confirmed.radioSource)
        assertEquals(1, confirmed.effectiveWifiAccessPoints().size)
    }

    @Test fun previousStaleStatePersistsWhenOnlyIpChanges() {
        val stale = GeoContextPolicy.changedLocation(first, first.copy(
            countryCode = "JP", lastSyncCity = "Tokyo",
        ))
        val updated = GeoContextPolicy.changedLocation(stale, stale.copy(lastSyncIp = "203.0.113.6"))
        assertTrue(updated.radioStale)
        assertEquals(stale.geoRevision, updated.geoRevision)
    }
}
