package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRuntimeSnapshotTest {
    @Test
    fun legacyProfilesAreMappedToSystemUser() {
        val profile = GeoProfile(targetPackage = "com.example.map", latitude = 34.0522, longitude = -118.2437)

        val snapshot = SystemRuntimeSnapshot.fromLegacyProfiles(listOf(profile))

        assertSame(profile, snapshot.profileFor(ProfileKey.SYSTEM_USER_ID, "com.example.map"))
        assertNull(snapshot.profileFor(10, "com.example.map"))
    }

    @Test
    fun strictSessionIsRuntimeOnlyAndRequiresAnEnabledLocationProfile() {
        val enabled = GeoProfile(targetPackage = "com.example.map", enabled = true, locationEnabled = true)
        val disabled = GeoProfile(targetPackage = "com.example.disabled", enabled = false, locationEnabled = true)
        val snapshot = SystemRuntimeSnapshot.fromLegacyProfiles(listOf(enabled, disabled))

        val armed = snapshot.armStrictSession(ProfileKey.legacy("com.example.map"), 1234L)
        assertEquals(ProfileKey.legacy("com.example.map"), armed.strictSession?.profileKey)
        assertSame(enabled, armed.strictProfile())

        val refused = snapshot.armStrictSession(ProfileKey.legacy("com.example.disabled"), 1234L)
        assertNull(refused.strictSession)
        assertTrue(armed.clearStrictSession().strictSession == null)
    }
}
