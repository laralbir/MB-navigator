package org.mbnavigator.android.core.geo

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint

class ElevationMetricsCalculatorTest {

    @Test
    fun test_small_oscillations_under_threshold_are_filtered() {
        // Alturas oscilando +/- 1 metro (ruido barométrico)
        val points = listOf(
            GeoPoint(0.0, 0.0, elevationMeters = 100.0),
            GeoPoint(0.0, 0.1, elevationMeters = 101.5),
            GeoPoint(0.0, 0.2, elevationMeters = 99.8),
            GeoPoint(0.0, 0.3, elevationMeters = 100.2)
        )

        val result = ElevationMetricsCalculator.calculate(points, noiseThresholdMeters = 3.0)
        assertEquals(0.0, result.gainMeters, 0.01)
        assertEquals(0.0, result.lossMeters, 0.01)
    }

    @Test
    fun test_real_climb_and_descent_accumulates_correctly() {
        val points = listOf(
            GeoPoint(0.0, 0.0, elevationMeters = 100.0),
            GeoPoint(0.0, 0.1, elevationMeters = 250.0), // +150 m
            GeoPoint(0.0, 0.2, elevationMeters = 200.0)  // -50 m
        )

        val result = ElevationMetricsCalculator.calculate(points, noiseThresholdMeters = 3.0)
        assertEquals(150.0, result.gainMeters, 0.01)
        assertEquals(50.0, result.lossMeters, 0.01)
    }
}
