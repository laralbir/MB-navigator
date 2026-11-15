package org.mbnavigator.android.core.traffic

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.HazardCategory
import org.mbnavigator.android.core.routing.TrafficIncidentPoint
import kotlin.math.roundToInt

data class UpcomingSafetyAlert(
    val incident: TrafficIncidentPoint,
    val distanceMeters: Double,
    val alertThresholdMeters: Int
)

/**
 * Evalúa avisos de seguridad críticos (radares, vehículos averiados / V16, accidentes, obstáculos)
 * de forma independiente al estado de la guía de giros por voz.
 */
object SafetyHazardAlertEngine {

    private const val MAX_ALERT_RADIUS_METERS = 900.0
    private const val MAX_LATERAL_FROM_ROUTE_METERS = 250.0

    fun evaluateActiveHazard(
        currentPosition: GeoPoint,
        routePoints: List<GeoPoint>?,
        incidents: List<TrafficIncidentPoint>,
        dgtAlertsEnabled: Boolean,
        speedCameraAlertsEnabled: Boolean
    ): UpcomingSafetyAlert? {
        if (incidents.isEmpty()) return null

        val candidates = incidents.mapNotNull { inc ->
            if (!isCategoryEnabled(inc.category, dgtAlertsEnabled, speedCameraAlertsEnabled)) {
                return@mapNotNull null
            }
            if (!inc.category.isCriticalSafetyHazard) return@mapNotNull null

            val dist = HaversineDistanceCalculator.distanceMeters(currentPosition, inc.point)
            val maxDist = if (inc.category == HazardCategory.SPEED_CAMERA) 800.0 else MAX_ALERT_RADIUS_METERS
            if (dist > maxDist) return@mapNotNull null

            if (!routePoints.isNullOrEmpty()) {
                val distToRoute = routePoints.minOfOrNull {
                    HaversineDistanceCalculator.distanceMeters(it, inc.point)
                } ?: Double.MAX_VALUE
                if (distToRoute > MAX_LATERAL_FROM_ROUTE_METERS) return@mapNotNull null
            }

            val threshold = when {
                dist <= 250.0 -> 250
                dist <= 550.0 -> 550
                else -> 900
            }
            UpcomingSafetyAlert(
                incident = inc,
                distanceMeters = dist,
                alertThresholdMeters = threshold
            )
        }

        // Priorizar por urgencia de peligro (radar/accidente/averiado más cercano)
        return candidates.minWithOrNull(
            compareBy<UpcomingSafetyAlert> { priorityOrder(it.incident.category) }
                .thenBy { it.distanceMeters }
        )
    }

    fun isCategoryEnabled(
        category: HazardCategory,
        dgtAlertsEnabled: Boolean,
        speedCameraAlertsEnabled: Boolean
    ): Boolean {
        return when (category) {
            HazardCategory.SPEED_CAMERA -> speedCameraAlertsEnabled
            HazardCategory.CONGESTION -> false
            else -> dgtAlertsEnabled
        }
    }

    fun buildAlertDeduplicationKey(alert: UpcomingSafetyAlert): String {
        return "${alert.incident.id}:${alert.alertThresholdMeters}"
    }

    /**
     * Devuelve true si debe emitirse aviso sonoro/crítico.
     * Nota: No depende de `voiceGuidanceEnabled`: incluso con indicaciones de giro silenciadas (`false`),
     * radares, accidentes y vehículos averiados se notifican salvo desactivación expresa en Seguridad.
     */
    fun shouldAnnounceSafetyAlert(
        alert: UpcomingSafetyAlert,
        dgtAlertsEnabled: Boolean,
        speedCameraAlertsEnabled: Boolean,
        announcedKeys: Set<String>
    ): Boolean {
        if (!isCategoryEnabled(alert.incident.category, dgtAlertsEnabled, speedCameraAlertsEnabled)) {
            return false
        }
        return !announcedKeys.contains(buildAlertDeduplicationKey(alert))
    }

    fun formatHazardSpeech(alert: UpcomingSafetyAlert): String {
        val inc = alert.incident
        val roundedDist = (((alert.distanceMeters.roundToInt()) + 25) / 50 * 50).coerceAtLeast(50)
        val roadSuffix = if (inc.roadName.isNotBlank()) " en ${inc.roadName}" else ""
        return when (inc.category) {
            HazardCategory.SPEED_CAMERA -> {
                val limitText = inc.speedLimitKmh?.let { " límite $it kilómetros por hora" }.orEmpty()
                "Atención, radar$limitText a $roundedDist metros$roadSuffix."
            }
            HazardCategory.BROKEN_DOWN_VEHICLE ->
                "Peligro, vehículo averiado a $roundedDist metros$roadSuffix."
            HazardCategory.ACCIDENT ->
                "Peligro, accidente en calzada a $roundedDist metros$roadSuffix."
            HazardCategory.OBSTACLE ->
                "Atención, obstáculo en calzada a $roundedDist metros$roadSuffix."
            HazardCategory.ROAD_CLOSURE ->
                "Atención, corte de carretera a $roundedDist metros$roadSuffix."
            HazardCategory.ROADWORK ->
                "Precaución, obras a $roundedDist metros$roadSuffix."
            HazardCategory.WEATHER_HAZARD ->
                "Precaución meteorológica a $roundedDist metros$roadSuffix."
            HazardCategory.CONGESTION ->
                "Retención a $roundedDist metros$roadSuffix."
        }
    }

    private fun priorityOrder(category: HazardCategory): Int {
        return when (category) {
            HazardCategory.ACCIDENT -> 0
            HazardCategory.BROKEN_DOWN_VEHICLE -> 1
            HazardCategory.SPEED_CAMERA -> 2
            HazardCategory.ROAD_CLOSURE -> 3
            HazardCategory.OBSTACLE -> 4
            HazardCategory.ROADWORK -> 5
            HazardCategory.WEATHER_HAZARD -> 6
            HazardCategory.CONGESTION -> 7
        }
    }
}
