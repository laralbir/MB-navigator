package org.mbnavigator.android.core.routing

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrackSegment
import org.mbnavigator.android.core.model.TrackStatistics
import org.mbnavigator.android.core.model.Waypoint

interface RoutingEngine {
    /**
     * Calcula una ruta garantizando el paso obligatorio por [viaPoints] en el orden provisto.
     */
    suspend fun calculateRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        viaPoints: List<Waypoint> = emptyList(),
        profile: String = "car-fast"
    ): Result<Track>
}

/**
 * Motor de ruteo para pruebas unitarias TDD.
 */
class MockRoutingEngine : RoutingEngine {

    override suspend fun calculateRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        viaPoints: List<Waypoint>,
        profile: String
    ): Result<Track> {
        val points = mutableListOf<GeoPoint>()
        points.add(origin)
        for (via in viaPoints) {
            points.add(via.point)
        }
        points.add(destination)

        val totalDist = HaversineDistanceCalculator.totalDistanceMeters(points)
        val estimatedDuration = if (totalDist > 0) (totalDist / 25.0).toLong() else 0L // ~90 km/h promedio

        val track = Track(
            name = "Ruta Calculada",
            segments = listOf(TrackSegment(points)),
            waypoints = viaPoints,
            statistics = TrackStatistics(
                totalDistanceMeters = totalDist,
                durationSeconds = estimatedDuration,
                avgSpeedKmh = 90.0
            )
        )
        return Result.success(track)
    }
}
