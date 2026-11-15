package org.mbnavigator.android.core.geo

import org.mbnavigator.android.core.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Utilidad matemática para cálculo de distancias geodésicas en superficie terrestre.
 */
object HaversineDistanceCalculator {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calcula la distancia en metros entre dos puntos geográficos usando la fórmula del semiverseno.
     */
    fun distanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calcula la distancia total acumulada de una secuencia ordenada de puntos.
     */
    fun totalDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceMeters(points[i], points[i + 1])
        }
        return total
    }
}
