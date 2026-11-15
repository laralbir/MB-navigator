package org.mbnavigator.android.core.model

import java.time.Instant
import java.util.UUID

/**
 * Registro histórico de telemetría y condiciones de un viaje finalizado.
 */
data class TripTelemetryRecord(
    val id: String = UUID.randomUUID().toString(),
    val tripTitle: String,
    val startTime: Instant,
    val endTime: Instant,
    val totalDistanceMeters: Double,
    val movingTimeSeconds: Long,
    val stoppedTimeSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minTempCelsius: Double? = null,
    val maxTempCelsius: Double? = null,
    val maxWindSpeedKmh: Double? = null,
    val adverseWeatherEncountered: Boolean = false,
    val recordedTrackId: String? = null
)
