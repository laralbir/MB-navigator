package org.mbnavigator.android.core.geo

import org.mbnavigator.android.core.model.GeoPoint

data class ElevationGainLoss(
    val gainMeters: Double,
    val lossMeters: Double
)

/**
 * Filtra el ruido barométrico y de precisión vertical del GPS para calcular desnivel acumulado (+D / -D).
 */
object ElevationMetricsCalculator {

    /**
     * Calcula la ganancia (+D) y pérdida (-D) acumulada de elevación en metros.
     * Solo suma variaciones absolutas superiores a [noiseThresholdMeters] para evitar sumar oscilaciones parásitas.
     */
    fun calculate(points: List<GeoPoint>, noiseThresholdMeters: Double = 3.0): ElevationGainLoss {
        val validPoints = points.mapNotNull { it.elevationMeters }
        if (validPoints.size < 2) return ElevationGainLoss(0.0, 0.0)

        var totalGain = 0.0
        var totalLoss = 0.0
        var lastValidElevation = validPoints.first()

        for (i in 1 until validPoints.size) {
            val currentElevation = validPoints[i]
            val delta = currentElevation - lastValidElevation

            if (kotlin.math.abs(delta) >= noiseThresholdMeters) {
                if (delta > 0) {
                    totalGain += delta
                } else {
                    totalLoss += kotlin.math.abs(delta)
                }
                lastValidElevation = currentElevation
            }
        }

        return ElevationGainLoss(gainMeters = totalGain, lossMeters = totalLoss)
    }
}
