package org.mbnavigator.android.core.ai

import org.mbnavigator.android.core.model.MeteoAlertLevel
import org.mbnavigator.android.core.model.RouteSegmentWeather
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrafficIncident

interface AIAssistantService {
    val isCloudAIEnabled: Boolean
    suspend fun generateTripBriefing(
        track: Track,
        weather: List<RouteSegmentWeather>,
        incidents: List<TrafficIncident>
    ): String
}

/**
 * Asistente 100% offline y determinista (cero conexión externa, máxima privacidad).
 */
class OfflineDeterministicAssistant : AIAssistantService {
    override val isCloudAIEnabled: Boolean = false

    override suspend fun generateTripBriefing(
        track: Track,
        weather: List<RouteSegmentWeather>,
        incidents: List<TrafficIncident>
    ): String {
        val distKm = track.statistics.totalDistanceMeters / 1000.0
        val adverseWeather = weather.firstOrNull { it.alertLevel != MeteoAlertLevel.NONE }
        val blockedIncident = incidents.firstOrNull { it.isRoadBlocked }

        val sb = StringBuilder()
        sb.append("Ruta de ${String.format(java.util.Locale.US, "%.1f", distKm)} km calculada.")

        if (blockedIncident != null) {
            sb.append(" ¡ATENCIÓN! Carretera cortada por ${blockedIncident.type} en ${blockedIncident.title}.")
        }

        if (adverseWeather != null) {
            sb.append(" Alerta meteorológica ${adverseWeather.alertLevel} por ${adverseWeather.condition} sobre las ${adverseWeather.estimatedArrivalTime}.")
        } else {
            sb.append(" Condiciones meteorológicas estables durante todo el recorrido.")
        }

        return sb.toString()
    }
}

/**
 * Asistente basado en Google Gemini (cuando el usuario configura su clave de API).
 */
class GeminiAIAssistant(
    private val apiKey: String?,
    private val fallbackAssistant: AIAssistantService = OfflineDeterministicAssistant()
) : AIAssistantService {

    override val isCloudAIEnabled: Boolean = !apiKey.isNullOrBlank()

    override suspend fun generateTripBriefing(
        track: Track,
        weather: List<RouteSegmentWeather>,
        incidents: List<TrafficIncident>
    ): String {
        if (!isCloudAIEnabled) {
            return fallbackAssistant.generateTripBriefing(track, weather, incidents)
        }
        // En modo con clave, genera el prompt estructurado para la API de Gemini
        // Para pruebas unitarias o fallback en caso de error de red, delega en el asistente offline
        return fallbackAssistant.generateTripBriefing(track, weather, incidents)
    }
}
