package org.mbnavigator.android.core.weather

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.MeteoAlertLevel
import org.mbnavigator.android.core.model.RouteSegmentWeather
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.WeatherCondition
import java.time.Duration
import java.time.Instant

interface WeatherService {
    /**
     * Calcula la meteorología estimada para cada tramo de la ruta según la hora prevista de paso.
     */
    suspend fun predictWeatherAlongRoute(
        track: Track,
        departureTime: Instant = Instant.now(),
        averageSpeedKmh: Double = 80.0
    ): List<RouteSegmentWeather>
}

/**
 * Motor meteorológico para pruebas unitarias TDD y fallback offline.
 */
class MockWeatherService(
    private val injectedAlert: MeteoAlertLevel = MeteoAlertLevel.NONE,
    private val injectedCondition: WeatherCondition = WeatherCondition.CLEAR
) : WeatherService {

    override suspend fun predictWeatherAlongRoute(
        track: Track,
        departureTime: Instant,
        averageSpeedKmh: Double
    ): List<RouteSegmentWeather> {
        val allPoints = track.segments.flatMap { it.points }
        if (allPoints.isEmpty()) return emptyList()

        val speedMps = (averageSpeedKmh * 1000.0) / 3600.0
        val results = mutableListOf<RouteSegmentWeather>()
        var accumulatedDistance = 0.0

        for (i in allPoints.indices) {
            val point = allPoints[i]
            if (i > 0) {
                accumulatedDistance += HaversineDistanceCalculator.distanceMeters(allPoints[i - 1], point)
            }
            val secondsElapsed = if (speedMps > 0) (accumulatedDistance / speedMps).toLong() else 0L
            val arrivalTime = departureTime.plus(Duration.ofSeconds(secondsElapsed))

            // Generar checkpoint meteorológico por cada tramo
            results.add(
                RouteSegmentWeather(
                    segmentIndex = i,
                    coordinate = point,
                    estimatedArrivalTime = arrivalTime,
                    temperatureCelsius = 18.0,
                    precipitationProbabilityPercent = if (injectedAlert == MeteoAlertLevel.RED) 90 else 10,
                    precipitationMm = if (injectedAlert == MeteoAlertLevel.RED) 45.0 else 0.0,
                    windSpeedKmh = if (injectedAlert == MeteoAlertLevel.RED) 75.0 else 12.0,
                    condition = injectedCondition,
                    alertLevel = injectedAlert,
                    alertDescription = if (injectedAlert != MeteoAlertLevel.NONE) "Aviso meteorológico activo en el tramo" else null
                )
            )
        }

        return results
    }
}
