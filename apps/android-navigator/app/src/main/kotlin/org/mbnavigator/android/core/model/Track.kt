package org.mbnavigator.android.core.model

import java.time.Instant
import java.util.UUID

/**
 * Segmento continuo de puntos de track.
 */
data class TrackSegment(
    val points: List<GeoPoint> = emptyList()
)

/**
 * Estadísticas calculadas de un track.
 */
data class TrackStatistics(
    val totalDistanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val durationSeconds: Long = 0L,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0
)

/**
 * Representación canónica de una ruta o recorrido registrado.
 */
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val createdAt: Instant = Instant.now(),
    val segments: List<TrackSegment> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val statistics: TrackStatistics = TrackStatistics()
)
