package org.mbnavigator.android.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.voice.StandardVoiceGuidanceService
import org.mbnavigator.android.core.voice.VoiceVerbosityLevel

class VoiceVerbosityTest {

    @Test
    fun test_minimal_verbosity_suppresses_distant_non_critical_announcements() {
        val voiceService = StandardVoiceGuidanceService(VoiceVerbosityLevel.MINIMAL)

        // No debe avisar giros a 500 m en modo mínimo
        assertFalse(voiceService.shouldAnnounce(distanceMeters = 500, isCritical = false))
        assertFalse(voiceService.shouldAnnounce(distanceMeters = 150, isCritical = false))

        // Sí debe avisar cuando el giro es inminente (<= 100 m)
        assertTrue(voiceService.shouldAnnounce(distanceMeters = 90, isCritical = false))

        // Alertas críticas (peligro inmediato, vehículo detenido V16) siempre deben sonar
        assertTrue(voiceService.shouldAnnounce(distanceMeters = 800, isCritical = true))
    }

    @Test
    fun test_detailed_verbosity_allows_early_turn_warnings() {
        val voiceService = StandardVoiceGuidanceService(VoiceVerbosityLevel.DETAILED)

        assertTrue(voiceService.shouldAnnounce(distanceMeters = 1200, isCritical = false))
        assertTrue(voiceService.shouldAnnounce(distanceMeters = 500, isCritical = false))
        assertTrue(voiceService.shouldAnnounce(distanceMeters = 50, isCritical = false))
    }
}
