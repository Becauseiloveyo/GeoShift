package io.geoshift.app.hooks

import io.geoshift.app.core.ProfileKey
import io.geoshift.app.core.SystemRuntimeSnapshot

internal data class SystemDeliveryTarget(
    val profileKey: ProfileKey,
    val strict: Boolean,
)

/** Pure identity selection used before any asynchronous callback loses Binder identity. */
internal object SystemProfileSelector {
    fun select(
        snapshot: SystemRuntimeSnapshot,
        userId: Int,
        ownedPackages: Set<String>,
        stringArguments: Iterable<String>,
    ): SystemDeliveryTarget? {
        snapshot.strictSession?.let { return SystemDeliveryTarget(it.profileKey, strict = true) }

        return stringArguments.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter(ownedPackages::contains)
            .mapNotNull { packageName ->
                val key = runCatching { ProfileKey(userId, packageName) }.getOrNull() ?: return@mapNotNull null
                val profile = snapshot.profiles[key] ?: return@mapNotNull null
                if (!profile.enabled || !profile.locationEnabled) null else SystemDeliveryTarget(key, strict = false)
            }
            .firstOrNull()
    }
}
