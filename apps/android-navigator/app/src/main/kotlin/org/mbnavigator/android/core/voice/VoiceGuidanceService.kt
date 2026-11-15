package org.mbnavigator.android.core.voice

enum class VoiceVerbosityLevel {
    MINIMAL,   // Solo peligro crítico y giros a < 100 m
    STANDARD,  // Aviso previo (500 m / 1 km) + confirmación de giro
    DETAILED   // Carriles, referencias, advertencias meteorológicas y nombre completo de vía
}

interface VoiceGuidanceService {
    var verbosityLevel: VoiceVerbosityLevel
    fun announceManeuver(instruction: String, distanceMeters: Int)
    fun announceAlert(alertMessage: String, isCritical: Boolean)
    fun shouldAnnounce(distanceMeters: Int, isCritical: Boolean): Boolean
}

class StandardVoiceGuidanceService(
    override var verbosityLevel: VoiceVerbosityLevel = VoiceVerbosityLevel.STANDARD
) : VoiceGuidanceService {

    private val announcements = mutableListOf<String>()
    val recordedAnnouncements: List<String> get() = announcements

    override fun shouldAnnounce(distanceMeters: Int, isCritical: Boolean): Boolean {
        if (isCritical) return true
        return when (verbosityLevel) {
            VoiceVerbosityLevel.MINIMAL -> distanceMeters <= 100
            VoiceVerbosityLevel.STANDARD -> distanceMeters <= 500
            VoiceVerbosityLevel.DETAILED -> distanceMeters <= 1500
        }
    }

    override fun announceManeuver(instruction: String, distanceMeters: Int) {
        if (shouldAnnounce(distanceMeters, isCritical = false)) {
            announcements.add("Maneuver: $instruction at $distanceMeters m")
        }
    }

    override fun announceAlert(alertMessage: String, isCritical: Boolean) {
        if (shouldAnnounce(distanceMeters = 0, isCritical = isCritical)) {
            announcements.add("Alert: $alertMessage")
        }
    }
}
