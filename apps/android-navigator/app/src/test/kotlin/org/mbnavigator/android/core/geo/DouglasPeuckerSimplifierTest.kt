package org.mbnavigator.android.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint

class DouglasPeuckerSimplifierTest {

    @Test
    fun test_collinear_points_are_reduced_to_start_and_end() {
        // Tres puntos en línea recta sobre el ecuador
        val p1 = GeoPoint(0.0, 0.0)
        val p2 = GeoPoint(0.0, 0.5)
        val p3 = GeoPoint(0.0, 1.0)

        val simplified = DouglasPeuckerSimplifier.simplify(listOf(p1, p2, p3), toleranceMeters = 50.0)
        assertEquals(2, simplified.size)
        assertEquals(p1, simplified.first())
        assertEquals(p3, simplified.last())
    }

    @Test
    fun test_significant_deviation_is_preserved() {
        val p1 = GeoPoint(0.0, 0.0)
        val p2 = GeoPoint(0.1, 0.5) // Desvío notable de ~11 km al norte
        val p3 = GeoPoint(0.0, 1.0)

        val simplified = DouglasPeuckerSimplifier.simplify(listOf(p1, p2, p3), toleranceMeters = 100.0)
        assertEquals(3, simplified.size)
        assertEquals(p2, simplified[1])
    }
}
