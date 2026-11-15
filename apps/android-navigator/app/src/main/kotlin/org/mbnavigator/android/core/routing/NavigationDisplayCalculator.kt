package org.mbnavigator.android.core.routing

import kotlin.math.abs

/**
 * Calcula el nivel de zoom óptimo de navegación en motocicleta en función de la velocidad (km/h)
 * y gestiona la histéresis para evitar cambios bruscos mientras se conduce.
 */
object NavigationDisplayCalculator {

    const val MIN_ZOOM_DELTA_THRESHOLD = 0.35

    fun computeTargetZoomForSpeed(speedKmh: Float): Double {
        val v = speedKmh.coerceAtLeast(0f)
        return when {
            v < 25f -> 17.2
            v < 55f -> interpolate(v, 25f, 55f, 17.2, 16.0)
            v < 90f -> interpolate(v, 55f, 90f, 16.0, 14.8)
            v < 125f -> interpolate(v, 90f, 125f, 14.8, 13.7)
            else -> 13.4
        }
    }

    fun shouldUpdateAutoZoom(currentZoom: Double, targetZoom: Double): Boolean {
        return abs(currentZoom - targetZoom) >= MIN_ZOOM_DELTA_THRESHOLD
    }

    private fun interpolate(
        value: Float,
        minSpeed: Float,
        maxSpeed: Float,
        zoomAtMin: Double,
        zoomAtMax: Double
    ): Double {
        val ratio = ((value - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f).toDouble()
        return zoomAtMin + (zoomAtMax - zoomAtMin) * ratio
    }
}
