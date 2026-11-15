package org.mbnavigator.android.core.traffic

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.IncidentType
import org.mbnavigator.android.core.model.SpeedCamera
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrafficIncident
import org.mbnavigator.android.core.model.TrafficSource

interface TrafficAlertService {
    /**
     * Devuelve las incidencias activas (cortes, obras, balizas V16) en un radio de proximidad sobre la ruta.
     */
    suspend fun getIncidentsAlongRoute(track: Track, proximityMeters: Double = 500.0): List<TrafficIncident>

    /**
     * Devuelve los radares de velocidad cercanos al tramo especificado.
     */
    suspend fun getSpeedCamerasNear(point: GeoPoint, radiusMeters: Double = 1500.0): List<SpeedCamera>
}

/**
 * Servicio simulado de tráfico para pruebas TDD.
 */
class MockTrafficAlertService(
    private val injectedIncidents: List<TrafficIncident> = emptyList(),
    private val injectedCameras: List<SpeedCamera> = emptyList()
) : TrafficAlertService {

    override suspend fun getIncidentsAlongRoute(track: Track, proximityMeters: Double): List<TrafficIncident> {
        val routePoints = track.segments.flatMap { it.points }
        return injectedIncidents.filter { incident ->
            routePoints.any { point ->
                HaversineDistanceCalculator.distanceMeters(point, incident.location) <= proximityMeters
            }
        }
    }

    override suspend fun getSpeedCamerasNear(point: GeoPoint, radiusMeters: Double): List<SpeedCamera> {
        return injectedCameras.filter { camera ->
            HaversineDistanceCalculator.distanceMeters(point, camera.location) <= radiusMeters
        }
    }
}
