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

    fun normalizeAngle360(degrees: Float): Float {
        val rem = degrees % 360f
        return if (rem < 0f) rem + 360f else rem
    }

    /**
     * Calcula el desplazamiento angular más corto [-180, +180] evitando giros completos
     * al cruzar el norte geográfico (359° -> 1° = +2° en lugar de -358°).
     */
    fun shortestAngleDeltaDegrees(fromDegrees: Float, toDegrees: Float): Float {
        val from = normalizeAngle360(fromDegrees)
        val to = normalizeAngle360(toDegrees)
        var delta = to - from
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    fun shouldUpdateHeadingBearing(speedKmh: Float, isSimulation: Boolean): Boolean {
        return isSimulation || speedKmh >= 6.0f
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
