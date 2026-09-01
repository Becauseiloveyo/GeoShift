package io.geoshift.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun zeroDistanceIsZero() {
        assertEquals(0, GeoMath.distanceMeters(34.0522, -118.2437, 34.0522, -118.2437))
    }

    @Test
    fun boundingBoxContainsCenterAndStaysValid() {
        val box = GeoMath.boundingBox(34.0522, -118.2437, 900)
        assertTrue(34.0522 in box.minLat..box.maxLat)
        assertTrue(-118.2437 in box.minLon..box.maxLon)
        assertTrue(box.minLat >= -90.0 && box.maxLat <= 90.0)
        assertTrue(box.minLon >= -180.0 && box.maxLon <= 180.0)
    }
}
