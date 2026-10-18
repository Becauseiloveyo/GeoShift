package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRevisionPolicyTest {
    private val first = GeoProfile(
        targetPackage = "com.example.lab", countryCode = "US",
        latitude = 34.0522, longitude = -118.2437, revision = 5L, geoRevision = 2L,
        radioGeoRevision = 2L, wifiEnabled = true, wifiBssid = "02:11:22:33:44:55",
    )

    @Test fun olderBackgroundSyncCannotOverwriteManualChange() {
        val newer = ProfileRevisionPolicy.next(first, first.copy(wifiSsid = "new"))!!
        assertEquals(6L, newer.revision)
        assertNull(ProfileRevisionPolicy.next(newer, first.copy(lastSyncIp = "203.0.113.7")))
    }

    @Test fun sameGeoCommitAdvancesRevisionWithoutStalingRadio() {
        val committed = ProfileRevisionPolicy.next(first, first.copy(lastSyncIp = "203.0.113.8"))!!
        assertEquals(6L, committed.revision)
        assertEquals(2L, committed.geoRevision)
        assertTrue(committed.isRadioCurrent())
    }

    @Test fun geographicalChangeIsDetectedEvenOutsideGeoIpSynchronizer() {
        val committed = ProfileRevisionPolicy.next(first, first.copy(countryCode = "JP"))!!
        assertEquals(3L, committed.geoRevision)
        assertTrue(committed.radioStale)
    }

    @Test fun newProfileStartsAtRevisionOneAndOverflowIsRejected() {
        val fresh = GeoProfile(targetPackage = "com.example.new")
        assertEquals(1L, ProfileRevisionPolicy.next(null, fresh)?.revision)
        assertNull(ProfileRevisionPolicy.next(first.copy(revision = Long.MAX_VALUE),
            first.copy(revision = Long.MAX_VALUE)))
    }

    @Test fun confirmationCanBePersistedAtCurrentGeography() {
        val moved = ProfileRevisionPolicy.next(first, first.copy(countryCode = "JP"))!!
        val confirmed = ProfileRevisionPolicy.next(moved, moved.confirmRadioForCurrentGeography("manual"))!!
        assertEquals(moved.geoRevision, confirmed.radioGeoRevision)
        assertFalse(confirmed.radioStale)
    }
}
