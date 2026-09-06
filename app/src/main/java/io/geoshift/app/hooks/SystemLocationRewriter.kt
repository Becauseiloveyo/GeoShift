package io.geoshift.app.hooks

import android.location.Location
import io.geoshift.app.core.GeoProfile

/**
 * Copies a framework Location and changes only profile-owned coordinates.
 * The original object is never mutated because it may be shared by system providers.
 */
internal object SystemLocationRewriter {
    fun rewrite(original: Location?, profile: GeoProfile?): Location? {
        if (original == null || profile == null || !profile.enabled || !profile.locationEnabled) return original
        if (profile.latitude !in -90.0..90.0 || profile.longitude !in -180.0..180.0) return original

        return Location(original).apply {
            latitude = profile.latitude
            longitude = profile.longitude
            if (!hasAccuracy()) accuracy = 5f
        }
    }

    fun rewriteCallbackValue(value: Any?, profile: GeoProfile?): Any? = when (value) {
        is Location -> rewrite(value, profile)
        is List<*> -> value.map { item ->
            if (item is Location) rewrite(item, profile) else item
        }
        else -> value
    }
}
