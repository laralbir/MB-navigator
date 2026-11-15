package org.mbnavigator.android.core.routing

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ManeuverType(val symbol: String) {
    DEPART("⬆"),
    STRAIGHT("⬆"),
    SLIGHT_RIGHT("↗"),
    RIGHT("↱"),
    SHARP_RIGHT("⤵"),
    SLIGHT_LEFT("↖"),
    LEFT("↰"),
    SHARP_LEFT("⤶"),
    ROUNDABOUT("⟳"),
    U_TURN("⮌"),
    ARRIVE("🏁")
}

data class RouteManeuver(
    val index: Int,
    val pointIndex: Int,
    val location: GeoPoint,
    val type: ManeuverType,
    val instruction: String,
    val roadName: String = "",
    val distanceFromStartMeters: Double
)

data class LiveNavigationProgress(
    val closestPointIndex: Int,
    val currentManeuver: RouteManeuver,
    val nextManeuverAfter: RouteManeuver?,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingMinutes: Int,
    val offRouteMeters: Double,
    val isOffRoute: Boolean,
    val hasArrived: Boolean
)

object TurnByTurnEngine {

    /**
     * Genera maniobras paso a paso a partir de cualquier trazado GPX/KML offline
     * analizando ángulos de giro y curvas pronunciadas de moto.
     */
    fun extractManeuversFromTrack(
        points: List<GeoPoint>,
        routeTitle: String = "la ruta"
    ): List<RouteManeuver> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) {
            return listOf(
                RouteManeuver(0, 0, points[0], ManeuverType.ARRIVE, "Destino alcanzado", "", 0.0)
            )
        }

        val cumulativeDistances = computeCumulativeDistances(points)
        val maneuvers = mutableListOf<RouteManeuver>()

        maneuvers.add(
            RouteManeuver(
                index = 0,
                pointIndex = 0,
                location = points.first(),
                type = ManeuverType.DEPART,
                instruction = "Inicia navegación por $routeTitle",
                distanceFromStartMeters = 0.0
            )
        )

        // Muestreamos vértices separados por al menos 40 m para evitar ruido GPS
        val sampledIndices = mutableListOf(0)
        var lastDist = 0.0
        for (i in 1 until points.size - 1) {
            if (cumulativeDistances[i] - lastDist >= 45.0) {
                sampledIndices.add(i)
                lastDist = cumulativeDistances[i]
            }
        }
        sampledIndices.add(points.lastIndex)

        var lastAddedManeuverDist = -100.0
        for (k in 1 until sampledIndices.size - 1) {
            val prevIdx = sampledIndices[k - 1]
            val currIdx = sampledIndices[k]
            val nextIdx = sampledIndices[k + 1]

            val inBearing = GpxKmlParser.bearingDegrees(points[prevIdx], points[currIdx])
            val outBearing = GpxKmlParser.bearingDegrees(points[currIdx], points[nextIdx])

            var delta = outBearing - inBearing
            while (delta > 180) delta -= 360
            while (delta < -180) delta += 360

            val absDelta = abs(delta)
            if (absDelta >= 32.0 && (cumulativeDistances[currIdx] - lastAddedManeuverDist) >= 70.0) {
                val type = when {
                    delta >= 120.0 -> ManeuverType.SHARP_RIGHT
                    delta >= 65.0 -> ManeuverType.RIGHT
                    delta >= 32.0 -> ManeuverType.SLIGHT_RIGHT
                    delta <= -120.0 -> ManeuverType.SHARP_LEFT
                    delta <= -65.0 -> ManeuverType.LEFT
                    else -> ManeuverType.SLIGHT_LEFT
                }
                val instruction = when (type) {
                    ManeuverType.SHARP_RIGHT -> "Horquilla / curva muy cerrada a la derecha"
                    ManeuverType.RIGHT -> "Gira a la derecha"
                    ManeuverType.SLIGHT_RIGHT -> "Curva a la derecha"
                    ManeuverType.SHARP_LEFT -> "Horquilla / curva muy cerrada a la izquierda"
                    ManeuverType.LEFT -> "Gira a la izquierda"
                    ManeuverType.SLIGHT_LEFT -> "Curva a la izquierda"
                    else -> "Continúa por el trazado"
                }
                maneuvers.add(
                    RouteManeuver(
                        index = maneuvers.size,
                        pointIndex = currIdx,
                        location = points[currIdx],
                        type = type,
                        instruction = instruction,
                        distanceFromStartMeters = cumulativeDistances[currIdx]
                    )
                )
                lastAddedManeuverDist = cumulativeDistances[currIdx]
            }
        }

        maneuvers.add(
            RouteManeuver(
                index = maneuvers.size,
                pointIndex = points.lastIndex,
                location = points.last(),
                type = ManeuverType.ARRIVE,
                instruction = "Llegada al destino de $routeTitle",
                distanceFromStartMeters = cumulativeDistances.last()
            )
        )

        return maneuvers
    }

    fun computeCumulativeDistances(points: List<GeoPoint>): DoubleArray {
        val result = DoubleArray(points.size)
        if (points.isEmpty()) return result
        result[0] = 0.0
        for (i in 1 until points.size) {
            result[i] = result[i - 1] + HaversineDistanceCalculator.distanceMeters(points[i - 1], points[i])
        }
        return result
    }

    fun evaluateProgress(
        currentPosition: GeoPoint,
        points: List<GeoPoint>,
        maneuvers: List<RouteManeuver>,
        currentSpeedKmh: Float
    ): LiveNavigationProgress? {
        if (points.isEmpty() || maneuvers.isEmpty()) return null

        val cumulative = computeCumulativeDistances(points)
        var bestIdx = 0
        var minDist = Double.MAX_VALUE

        for (i in points.indices) {
            val d = HaversineDistanceCalculator.distanceMeters(currentPosition, points[i])
            if (d < minDist) {
                minDist = d
                bestIdx = i
            }
        }
        // También comprobar distancia perpendicular a cada segmento por si los vértices están distanciados
        for (i in 0 until points.lastIndex) {
            val segDist = pointToSegmentMeters(currentPosition, points[i], points[i + 1])
            if (segDist < minDist) {
                minDist = segDist
                bestIdx = i + 1
            }
        }

        val currentAlongMeters = cumulative[bestIdx]
        val totalMeters = cumulative.last()
        val remainingTotal = (totalMeters - currentAlongMeters).coerceAtLeast(0.0)

        // Buscar la siguiente maniobra por delante de nuestra posición (margen 18 m tras pasar el punto)
        val nextIdxInManeuvers = maneuvers.indexOfFirst {
            it.distanceFromStartMeters > currentAlongMeters - 18.0 && it.index > 0
        }.let { if (it == -1) maneuvers.lastIndex else it }

        val targetManeuver = maneuvers[nextIdxInManeuvers]
        val followManeuver = if (nextIdxInManeuvers + 1 < maneuvers.size) maneuvers[nextIdxInManeuvers + 1] else null

        val distToTarget = if (minDist > 60.0) {
            // Si estamos desviados o lejos del inicio del track, medir distancia directa al primer punto/maniobra
            HaversineDistanceCalculator.distanceMeters(currentPosition, targetManeuver.location)
        } else {
            (targetManeuver.distanceFromStartMeters - currentAlongMeters).coerceAtLeast(0.0)
        }

        val effectiveSpeedKmh = if (currentSpeedKmh >= 18f) currentSpeedKmh.toDouble() else 65.0
        val etaMinutes = ((remainingTotal / 1000.0) / effectiveSpeedKmh * 60.0).roundToInt().coerceAtLeast(1)

        val arrived = bestIdx >= points.lastIndex && minDist < 40.0

        return LiveNavigationProgress(
            closestPointIndex = bestIdx,
            currentManeuver = targetManeuver,
            nextManeuverAfter = followManeuver,
            distanceToManeuverMeters = distToTarget,
            remainingDistanceMeters = remainingTotal,
            remainingMinutes = etaMinutes,
            offRouteMeters = minDist,
            isOffRoute = minDist > 60.0,
            hasArrived = arrived
        )
    }

    /**
     * Determina si debe emitirse un aviso de voz TTS en función de la distancia al próximo giro
     * y del último umbral ya anunciado para esa maniobra.
     * Retorna el umbral disparado (500, 150, 40) o null si no procede hablar aún.
     */
    fun determineVoicePromptThreshold(
        maneuverIndex: Int,
        distanceMeters: Double,
        lastSpokenPair: Pair<Int, Int>?
    ): Int? {
        val lastIdx = lastSpokenPair?.first ?: -1
        val lastThreshold = if (lastIdx == maneuverIndex) lastSpokenPair?.second ?: Int.MAX_VALUE else Int.MAX_VALUE

        return when {
            distanceMeters <= 45.0 && lastThreshold > 45 -> 45
            distanceMeters in 45.1..160.0 && lastThreshold > 160 -> 160
            distanceMeters in 160.1..520.0 && lastThreshold > 520 -> 520
            else -> null
        }
    }

    fun formatSpokenInstruction(maneuver: RouteManeuver, distanceMeters: Double): String {
        val d = distanceMeters.roundToInt()
        val roadSuffix = if (maneuver.roadName.isNotBlank()) " hacia ${maneuver.roadName}" else ""
        return when {
            maneuver.type == ManeuverType.ARRIVE && d <= 45 -> "Has llegado a tu destino."
            d <= 45 -> "${maneuver.instruction}$roadSuffix."
            d >= 1000 -> String.format("A %.1f kilómetros, %s%s.", d / 1000.0, maneuver.instruction.replaceFirstChar { it.lowercase() }, roadSuffix)
            else -> {
                val rounded = ((d + 25) / 50) * 50
                "En ${rounded.coerceAtLeast(50)} metros, ${maneuver.instruction.replaceFirstChar { it.lowercase() }}$roadSuffix."
            }
        }
    }

    private fun pointToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val midLatRad = Math.toRadians((a.latitude + b.latitude) * 0.5)
        val metersPerDegLat = 111320.0
        val metersPerDegLon = 111320.0 * cos(midLatRad)

        val ax = 0.0
        val ay = 0.0
        val bx = (b.longitude - a.longitude) * metersPerDegLon
        val by = (b.latitude - a.latitude) * metersPerDegLat
        val px = (p.longitude - a.longitude) * metersPerDegLon
        val py = (p.latitude - a.latitude) * metersPerDegLat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6) return HaversineDistanceCalculator.distanceMeters(p, a)

        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        val diffX = px - projX
        val diffY = py - projY
        return sqrt(diffX * diffX + diffY * diffY)
    }
}
