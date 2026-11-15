package org.mbnavigator.android.core.model

import java.time.Instant

enum class TrafficSource {
    DGT_3_0,         // Datos en tiempo real DGT (España / DATEX II)
    OPEN_STREET_MAP, // Elementos estáticos OSM
    COMMUNITY        // Reportes locales o comunitarios
}

enum class IncidentType {
    ACCIDENT,
    ROAD_CLOSURE,
    ROADWORK,
    V16_EMERGENCY_BEACON,  // Baliza V16 conectada activa en arcén/calzada
    OBSTACLE,
    HEAVY_CONGESTION,
    WEATHER_HAZARD
}

/**
 * Incidencia vial, radar o alerta de tráfico en tiempo real.
 */
data class TrafficIncident(
    val id: String,
    val source: TrafficSource,
    val location: GeoPoint,
    val type: IncidentType,
    val title: String,
    val description: String,
    val estimatedDelayMinutes: Int = 0,
    val isRoadBlocked: Boolean = false,
    val timestamp: Instant = Instant.now()
)

/**
 * Radar de velocidad (fijo, tramo o móvil habitual).
 */
data class SpeedCamera(
    val id: String,
    val location: GeoPoint,
    val speedLimitKmh: Int,
    val isAverageSpeedZone: Boolean = false,
    val description: String? = null
)
