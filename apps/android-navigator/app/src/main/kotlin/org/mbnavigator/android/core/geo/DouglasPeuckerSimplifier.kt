package org.mbnavigator.android.core.geo

import org.mbnavigator.android.core.model.GeoPoint
import kotlin.math.abs

/**
 * Algoritmo de Ramer-Douglas-Peucker para simplificación de polilíneas y reducción del número de vértices.
 */
object DouglasPeuckerSimplifier {

    /**
     * Simplifica una lista de GeoPoints descartando vértices cuya distancia ortogonal a la cuerda
     * sea inferior a [toleranceMeters].
     */
    fun simplify(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        if (points.size <= 2) return points

        var maxDistance = 0.0
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistance(points[i], start, end)
            if (d > maxDistance) {
                maxDistance = d
                index = i
            }
        }

        return if (maxDistance > toleranceMeters) {
            val rec1 = simplify(points.subList(0, index + 1), toleranceMeters)
            val rec2 = simplify(points.subList(index, points.size), toleranceMeters)
            rec1.dropLast(1) + rec2
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(point: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val lineDistance = HaversineDistanceCalculator.distanceMeters(lineStart, lineEnd)
        if (lineDistance == 0.0) {
            return HaversineDistanceCalculator.distanceMeters(point, lineStart)
        }

        val d1 = HaversineDistanceCalculator.distanceMeters(lineStart, point)
        val d2 = HaversineDistanceCalculator.distanceMeters(lineEnd, point)

        // Área del triángulo usando la fórmula de Herón
        val s = (d1 + d2 + lineDistance) / 2.0
        val areaSq = s * (s - d1) * (s - d2) * (s - lineDistance)
        val area = if (areaSq > 0) kotlin.math.sqrt(areaSq) else 0.0

        // Altura perpendicular = 2 * Área / Base
        return (2.0 * area) / lineDistance
    }
}
