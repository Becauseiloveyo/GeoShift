package io.geoshift.app.hooks

import io.geoshift.app.core.ProfileKey
import io.geoshift.app.core.SystemRuntimeSnapshot

internal data class SystemDeliveryTarget(val profileKey: ProfileKey)

/** Pure identity selection used before any asynchronous callback loses Binder identity. */
internal object SystemProfileSelector {
    fun select(
        snapshot: SystemRuntimeSnapshot,
        userId: Int,
        ownedPackages: Set<String>,
        stringArguments: Iterable<String>,
    ): SystemDeliveryTarget? {
        // Strict provider sessions never override Binder/UID-based per-app identity.
        val candidates = stringArguments.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter(ownedPackages::contains)
            .mapNotNull { packageName ->
                val key = runCatching { ProfileKey(userId, packageName) }.getOrNull() ?: return@mapNotNull null
                val profile = snapshot.profiles[key] ?: return@mapNotNull null
                if (!profile.enabled || !profile.locationEnabled) null else SystemDeliveryTarget(key)
            }
            .distinctBy { it.profileKey }
            .take(2)
            .toList()
        // Shared UIDs and brokered calls may carry more than one plausible package.
        // Ambiguous identities are NOT safe to attribute to a single profile.
        return candidates.singleOrNull()
    }
}
