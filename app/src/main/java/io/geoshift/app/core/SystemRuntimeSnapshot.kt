package io.geoshift.app.core

/**
 * Immutable state consumed by system-process hooks.
 *
 * Hook hot paths must only read this snapshot; network, disk and JSON work must
 * happen before publishing a replacement snapshot.
 */
data class SystemRuntimeSnapshot(
    val profiles: Map<ProfileKey, GeoProfile>,
    val strictSession: StrictMapSession? = null,
) {
    fun profileFor(userId: Int, packageName: String): GeoProfile? =
        runCatching { profiles[ProfileKey(userId, packageName)] }.getOrNull()

    /** Never return a global profile after the monotonic lease expires. */
    fun strictProfile(nowElapsedMs: Long): GeoProfile? =
        strictSession?.takeIf { it.isActive(nowElapsedMs) }
            ?.let { session -> profiles[session.profileKey] }
            ?.takeIf { it.enabled && it.locationEnabled }

    fun armStrictSession(
        profileKey: ProfileKey,
        startedAtElapsedMs: Long,
        durationMs: Long = StrictSessionPolicy.DEFAULT_DURATION_MS,
    ): SystemRuntimeSnapshot {
        val profile = profiles[profileKey] ?: return this
        if (!profile.enabled || !profile.locationEnabled ||
            !StrictSessionPolicy.isActive(startedAtElapsedMs, startedAtElapsedMs, durationMs)
        ) return this
        return copy(strictSession = StrictMapSession(profileKey, startedAtElapsedMs, durationMs))
    }

    fun clearStrictSession(): SystemRuntimeSnapshot = copy(strictSession = null)

    companion object {
        val EMPTY = SystemRuntimeSnapshot(emptyMap())

        fun fromLegacyProfiles(
            profiles: Iterable<GeoProfile>,
            userId: Int = ProfileKey.SYSTEM_USER_ID,
        ): SystemRuntimeSnapshot {
            val mapped = profiles
                .filter { it.targetPackage.isNotBlank() }
                .associateBy { ProfileKey(userId, it.targetPackage) }
            return SystemRuntimeSnapshot(mapped)
        }
    }
}

/**
 * Strict sessions are intentionally runtime-only in v0.4. They are never restored
 * automatically after a reboot; the user must arm one again for the current boot.
 */
data class StrictMapSession(
    val profileKey: ProfileKey,
    val startedAtElapsedMs: Long,
    val durationMs: Long,
) {
    fun isActive(nowElapsedMs: Long): Boolean =
        StrictSessionPolicy.isActive(nowElapsedMs, startedAtElapsedMs, durationMs)
}
