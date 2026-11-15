package org.mbnavigator.android.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint

class HaversineDistanceCalculatorTest {

    @Test
    fun test_same_point_returns_zero_distance() {
        val p = GeoPoint(40.4168, -3.7038) // Madrid Puerta del Sol
        val d = HaversineDistanceCalculator.distanceMeters(p, p)
        assertEquals(0.0, d, 0.001)
    }

    @Test
    fun test_madrid_to_barcelona_distance() {
        // Coordenadas conocidas:
        // Madrid: 40.4168, -3.7038
        // Barcelona: 41.3879, 2.16992
        // Distancia ortodrómica aproximada: ~505 km (+/- 3 km)
        val madrid = GeoPoint(40.4168, -3.7038)
        val barcelona = GeoPoint(41.3879, 2.16992)

        val distance = HaversineDistanceCalculator.distanceMeters(madrid, barcelona)
        assertTrue("Distance should be approx 505 km, was: $distance", distance in 500000.0..510000.0)
    }

    @Test
    fun test_total_distance_calculation() {
        val p1 = GeoPoint(0.0, 0.0)
        val p2 = GeoPoint(0.0, 1.0) // ~111 km sobre el ecuador
        val p3 = GeoPoint(0.0, 2.0) // ~111 km más

        val d1 = HaversineDistanceCalculator.distanceMeters(p1, p2)
        val total = HaversineDistanceCalculator.totalDistanceMeters(listOf(p1, p2, p3))

        assertEquals(d1 * 2, total, 0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test_invalid_latitude_throws_error() {
        GeoPoint(91.0, 0.0)
    }
}
