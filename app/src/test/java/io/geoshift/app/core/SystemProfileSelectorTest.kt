package io.geoshift.app.core

import io.geoshift.app.hooks.SystemProfileSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProfileSelectorTest {
    @Test
    fun directClientMustOwnThePackageItClaims() {
        val profile = GeoProfile(targetPackage = "com.example.map")
        val snapshot = SystemRuntimeSnapshot.fromLegacyProfiles(listOf(profile))

        val accepted = SystemProfileSelector.select(
            snapshot = snapshot,
            userId = 0,
            ownedPackages = setOf("com.example.map"),
            stringArguments = listOf("gps", "com.example.map", "tag"),
        )
        assertEquals(ProfileKey.legacy("com.example.map"), accepted?.profileKey)
        assertTrue(accepted?.strict == false)

        val rejected = SystemProfileSelector.select(
            snapshot = snapshot,
            userId = 0,
            ownedPackages = setOf("com.example.other"),
            stringArguments = listOf("gps", "com.example.map"),
        )
        assertNull(rejected)
    }

    @Test
    fun strictSessionWinsWithoutPretendingToBePerApp() {
        val profile = GeoProfile(targetPackage = "com.example.map")
        val strict = SystemRuntimeSnapshot.fromLegacyProfiles(listOf(profile))
            .armStrictSession(ProfileKey.legacy("com.example.map"), 1L)

        val selected = SystemProfileSelector.select(
            snapshot = strict,
            userId = 0,
            ownedPackages = emptySet(),
            stringArguments = emptyList(),
        )

        assertEquals(ProfileKey.legacy("com.example.map"), selected?.profileKey)
        assertTrue(selected?.strict == true)
    }
}
