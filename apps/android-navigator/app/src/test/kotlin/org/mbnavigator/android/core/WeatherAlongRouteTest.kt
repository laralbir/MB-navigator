package org.mbnavigator.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.MeteoAlertLevel
import org.mbnavigator.android.core.model.RouteSegmentWeather
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrackSegment
import org.mbnavigator.android.core.model.WeatherCondition
import org.mbnavigator.android.core.weather.MockWeatherService
import java.time.Instant

class WeatherAlongRouteTest {

    @Test
    fun test_weather_checkpoints_calculate_sequential_arrival_times() = runBlocking {
        val weatherService = MockWeatherService()

        val departureTime = Instant.parse("2026-11-15T12:00:00Z")
        val track = Track(
            name = "Ruta en moto",
            segments = listOf(
                TrackSegment(
                    listOf(
                        GeoPoint(40.0, -3.0),
                        GeoPoint(40.5, -3.0),
                        GeoPoint(41.0, -3.0)
                    )
                )
            )
        )

        val weatherResults = weatherService.predictWeatherAlongRoute(
            track = track,
            departureTime = departureTime,
            averageSpeedKmh = 100.0
        )

        assertEquals(3, weatherResults.size)
        assertEquals("First point arrival time equals departure time", departureTime, weatherResults[0].estimatedArrivalTime)
        assertTrue(
            "Second point arrival time is strictly after first",
            weatherResults[1].estimatedArrivalTime.isAfter(weatherResults[0].estimatedArrivalTime)
        )
        assertTrue(
            "Third point arrival time is strictly after second",
            weatherResults[2].estimatedArrivalTime.isAfter(weatherResults[1].estimatedArrivalTime)
        )
    }

    @Test
    fun test_severe_weather_alert_is_detected() = runBlocking {
        val weatherService = MockWeatherService(
            injectedAlert = MeteoAlertLevel.RED,
            injectedCondition = WeatherCondition.SNOW
        )

        val track = Track(
            name = "Puerto de montaña en moto",
            segments = listOf(
                TrackSegment(listOf(GeoPoint(42.5, 0.5), GeoPoint(42.6, 0.6)))
            )
        )

        val weather = weatherService.predictWeatherAlongRoute(track)
        val hasRedAlert = weather.any { it.alertLevel == MeteoAlertLevel.RED }

        assertTrue("Should detect red weather alert", hasRedAlert)
        assertEquals(WeatherCondition.SNOW, weather.first().condition)
    }

    @Test
    fun test_motorcycle_specific_crosswind_and_ice_hazard_flags() {
        val dangerousMountainSegment = RouteSegmentWeather(
            segmentIndex = 0,
            coordinate = GeoPoint(42.0, 0.0),
            estimatedArrivalTime = Instant.now(),
            temperatureCelsius = 1.5, // 1.5ºC cerca de congelación
            precipitationProbabilityPercent = 80,
            precipitationMm = 2.0, // lluvia en frío = riesgo de hielo
            windSpeedKmh = 38.0,   // Viento fuerte para moto
            windGustKmh = 55.0,    // Racha violenta lateral
            condition = WeatherCondition.RAIN_HEAVY,
            alertLevel = MeteoAlertLevel.ORANGE
        )

        assertTrue("Must flag crosswind hazard for motorcycle", dangerousMountainSegment.isCrosswindHazardForMotorcycle)
        assertTrue("Must flag wet road risk", dangerousMountainSegment.isWetRoadRisk)
        assertTrue("Must flag freezing black ice risk in cold asphalt", dangerousMountainSegment.isFreezingAsphaltRisk)
    }
}
