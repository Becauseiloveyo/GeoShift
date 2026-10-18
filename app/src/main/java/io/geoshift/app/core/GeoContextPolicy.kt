package io.geoshift.app.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure policy for deciding if existing Wi-Fi/cell identities belong to a previous
 * geographic environment. Conservative: ~1 km movement or a named region change
 * makes previously configured radio metadata stale, without deleting user data.
 */
object GeoContextPolicy {
    private const val MAX_SAME_PLACE_DISTANCE_KM = 1.0
    private const val EARTH_RADIUS_KM = 6371.0

    fun materiallyChanged(base: GeoProfile, updated: GeoProfile): Boolean {
        if (base.countryCode.trim().uppercase() != updated.countryCode.trim().uppercase()) return true
        if (base.lastSyncRegion.trim().lowercase() != updated.lastSyncRegion.trim().lowercase()) return true
        if (base.lastSyncCity.trim().lowercase() != updated.lastSyncCity.trim().lowercase()) return true
        if (!base.latitude.isFinite() || !base.longitude.isFinite() ||
            !updated.latitude.isFinite() || !updated.longitude.isFinite()) return true
        val latDistance = Math.toRadians(updated.latitude - base.latitude)
        val lonDistance = Math.toRadians(updated.longitude - base.longitude)
        val sinHalfLat = sin(latDistance / 2.0)
        val sinHalfLon = sin(lonDistance / 2.0)
        val a = sinHalfLat.pow(2.0) +
            cos(Math.toRadians(base.latitude)) * cos(Math.toRadians(updated.latitude)) * sinHalfLon.pow(2.0)
        val distanceKm = 2.0 * EARTH_RADIUS_KM * asin(sqrt(min(1.0, a.coerceAtLeast(0.0))))
        return distanceKm >= MAX_SAME_PLACE_DISTANCE_KM
    }

    fun changedLocation(base: GeoProfile, updated: GeoProfile): GeoProfile {
        if (!materiallyChanged(base, updated)) return updated.copy(
            geoRevision = base.geoRevision,
            radioGeoRevision = base.radioGeoRevision,
            radioStale = base.radioStale,
        )
        val next = if (base.geoRevision == Long.MAX_VALUE) 1L else base.geoRevision + 1L
        return updated.copy(
            geoRevision = next,
            radioGeoRevision = base.radioGeoRevision,
            radioStale = true,
        )
    }
}

fun GeoProfile.isRadioCurrent(): Boolean =
    !radioStale && radioGeoRevision == geoRevision

fun GeoProfile.confirmRadioForCurrentGeography(source: String): GeoProfile =
    copy(radioStale = false, radioGeoRevision = geoRevision, radioSource = source)
