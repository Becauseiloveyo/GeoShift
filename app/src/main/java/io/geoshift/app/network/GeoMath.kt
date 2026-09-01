package io.geoshift.app.network

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object GeoMath {
    data class BoundingBox(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
    )

    fun boundingBox(latitude: Double, longitude: Double, radiusMeters: Int): BoundingBox {
        val latDelta = radiusMeters / 111_320.0
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val lonDelta = radiusMeters / (111_320.0 * cosLat)
        return BoundingBox(
            minLat = (latitude - latDelta).coerceAtLeast(-90.0),
            minLon = (longitude - lonDelta).coerceAtLeast(-180.0),
            maxLat = (latitude + latDelta).coerceAtMost(90.0),
            maxLon = (longitude + lonDelta).coerceAtMost(180.0),
        )
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return (2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))).roundToInt()
    }
}
