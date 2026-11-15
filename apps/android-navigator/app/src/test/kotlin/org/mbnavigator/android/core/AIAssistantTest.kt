package org.mbnavigator.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.ai.GeminiAIAssistant
import org.mbnavigator.android.core.ai.OfflineDeterministicAssistant
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.IncidentType
import org.mbnavigator.android.core.model.MeteoAlertLevel
import org.mbnavigator.android.core.model.RouteSegmentWeather
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrackStatistics
import org.mbnavigator.android.core.model.TrafficIncident
import org.mbnavigator.android.core.model.TrafficSource
import org.mbnavigator.android.core.model.WeatherCondition
import java.time.Instant

class AIAssistantTest {

    @Test
    fun test_offline_assistant_generates_concise_briefing() = runBlocking {
        val assistant = OfflineDeterministicAssistant()

        val track = Track(
            name = "Prueba",
            statistics = TrackStatistics(totalDistanceMeters = 85000.0)
        )

        val weather = listOf(
            RouteSegmentWeather(
                segmentIndex = 0,
                coordinate = GeoPoint(40.0, -3.0),
                estimatedArrivalTime = Instant.now(),
                temperatureCelsius = 15.0,
                precipitationProbabilityPercent = 0,
                precipitationMm = 0.0,
                windSpeedKmh = 10.0,
                condition = WeatherCondition.CLEAR
            )
        )

        val briefing = assistant.generateTripBriefing(track, weather, emptyList())

        assertTrue(briefing.contains("85.0 km"))
        assertTrue(briefing.contains("Condiciones meteorológicas estables"))
    }

    @Test
    fun test_gemini_assistant_without_key_falls_back_to_offline_mode() = runBlocking {
        val assistant = GeminiAIAssistant(apiKey = null)
        assertFalse("Cloud AI should be disabled when key is null", assistant.isCloudAIEnabled)

        val track = Track(
            name = "Montaña",
            statistics = TrackStatistics(totalDistanceMeters = 30000.0)
        )

        val briefing = assistant.generateTripBriefing(track, emptyList(), emptyList())
        assertTrue("Briefing should be produced via fallback", briefing.isNotBlank())
    }
}
