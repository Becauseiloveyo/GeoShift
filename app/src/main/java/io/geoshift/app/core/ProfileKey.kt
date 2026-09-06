package io.geoshift.app.core

/**
 * Stable identity for a profile in multi-user Android environments.
 *
 * Existing v0.3 profiles are migrated into user 0 at runtime. Persistent per-user
 * storage is introduced separately so old installations remain readable.
 */
data class ProfileKey(
    val userId: Int,
    val packageName: String,
) {
    init {
        require(userId >= 0) { "userId must be non-negative" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }

    companion object {
        const val SYSTEM_USER_ID = 0

        fun legacy(packageName: String): ProfileKey =
            ProfileKey(SYSTEM_USER_ID, packageName.trim())
    }
}
