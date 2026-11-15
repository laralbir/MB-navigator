package org.mbnavigator.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.MeteoAlertLevel
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
            name = "Ruta de prueba",
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
            name = "Ruta de montaña",
            segments = listOf(
                TrackSegment(listOf(GeoPoint(42.5, 0.5), GeoPoint(42.6, 0.6)))
            )
        )

        val weather = weatherService.predictWeatherAlongRoute(track)
        val hasRedAlert = weather.any { it.alertLevel == MeteoAlertLevel.RED }

        assertTrue("Should detect red weather alert", hasRedAlert)
        assertEquals(WeatherCondition.SNOW, weather.first().condition)
    }
}
