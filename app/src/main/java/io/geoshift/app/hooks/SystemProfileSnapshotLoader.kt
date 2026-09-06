package io.geoshift.app.hooks

import android.content.SharedPreferences
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileKey
import io.geoshift.app.core.ProfileStore
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.SystemRuntimeSnapshot

/** Reads the existing v0.3 profile namespace without mutating it. */
internal object SystemProfileSnapshotLoader {
    private const val PROFILE_PREFIX = "profile::"
    private val targetSuffix = "::${ProfileStore.KEY_TARGET_PACKAGE}"

    fun load(
        prefs: SharedPreferences,
        userId: Int = ProfileKey.SYSTEM_USER_ID,
    ): SystemRuntimeSnapshot {
        val names = LinkedHashSet<String>()

        prefs.all.keys.forEach { key ->
            if (key.startsWith(PROFILE_PREFIX) && key.endsWith(targetSuffix)) {
                key.removePrefix(PROFILE_PREFIX)
                    .removeSuffix(targetSuffix)
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let(names::add)
            }
        }

        // Keep v0.1/v0.2 single-profile installations readable until migration runs in the app.
        prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "")
            .orEmpty()
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(names::add)

        val profiles = names.mapNotNull { packageName ->
            ProfileStoreV2.load(prefs, packageName)
                ?: legacyProfileFor(prefs, packageName)
        }
        return SystemRuntimeSnapshot.fromLegacyProfiles(profiles, userId)
    }

    private fun legacyProfileFor(prefs: SharedPreferences, packageName: String): GeoProfile? {
        val legacyTarget = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
        return if (legacyTarget == packageName) ProfileStore.load(prefs) else null
    }
}
