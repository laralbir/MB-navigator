package org.mbnavigator.android.core.routing

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrackSegment
import org.mbnavigator.android.core.model.TrackStatistics
import org.mbnavigator.android.core.model.Waypoint

/**
 * Perfiles de ruteo optimizados para motocicletas en BRouter.
 */
enum class MotorcycleRoutingProfile(val brouterProfileKey: String, val displayName: String) {
    CURVY("moto-curvy", "Carreteras Reviradas con Curvas"),
    EXTRA_CURVY_SCENIC("moto-scenic", "Puertos de Montaña y Panorámicas"),
    FAST_HIGHWAY("moto-fast", "Rápida (Autovía y Nacionales)"),
    ADVENTURE_TRAIL("moto-trail", "Trail y Pistas de Tierra (Offroad)")
}

interface RoutingEngine {
    /**
     * Calcula una ruta de moto garantizando el paso obligatorio por [viaPoints] en el orden provisto.
     */
    suspend fun calculateRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        viaPoints: List<Waypoint> = emptyList(),
        profile: MotorcycleRoutingProfile = MotorcycleRoutingProfile.CURVY
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
        profile: MotorcycleRoutingProfile
    ): Result<Track> {
        val points = mutableListOf<GeoPoint>()
        points.add(origin)
        for (via in viaPoints) {
            points.add(via.point)
        }
        points.add(destination)

        val totalDist = HaversineDistanceCalculator.totalDistanceMeters(points)
        // La velocidad estimada varía según si es ruta de curvas o autovía
        val avgSpeedKmh = when (profile) {
            MotorcycleRoutingProfile.CURVY -> 65.0
            MotorcycleRoutingProfile.EXTRA_CURVY_SCENIC -> 50.0
            MotorcycleRoutingProfile.FAST_HIGHWAY -> 110.0
            MotorcycleRoutingProfile.ADVENTURE_TRAIL -> 40.0
        }
        val speedMps = (avgSpeedKmh * 1000.0) / 3600.0
        val estimatedDuration = if (speedMps > 0) (totalDist / speedMps).toLong() else 0L

        val track = Track(
            name = "Ruta en Moto (${profile.displayName})",
            segments = listOf(TrackSegment(points)),
            waypoints = viaPoints,
            statistics = TrackStatistics(
                totalDistanceMeters = totalDist,
                durationSeconds = estimatedDuration,
                avgSpeedKmh = avgSpeedKmh
            )
        )
        return Result.success(track)
    }
}
