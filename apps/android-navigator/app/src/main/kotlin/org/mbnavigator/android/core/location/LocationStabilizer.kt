package org.mbnavigator.android.core.location

import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint

data class StabilizedLocationResult(
    val location: GeoPoint,
    val lastAccurateFixMs: Long,
    val moved: Boolean
)

/**
 * Filtro de precisión y estabilidad estática para coordenadas GNSS.
 * Evita saltos por ubicaciones de red gruesas y congela el cursor cuando la moto está parada.
 */
object LocationStabilizer {

    private const val STATIONARY_SPEED_THRESHOLD_MPS = 1.25f // ~4.5 km/h
    private const val RECENT_ACCURATE_WINDOW_MS = 15_000L

    fun stabilize(
        previousAnchored: GeoPoint?,
        lastAccurateFixMs: Long,
        candidate: GeoPoint,
        provider: String,
        nowMs: Long
    ): StabilizedLocationResult {
        val candidateAccuracy = candidate.accuracyMeters ?: 8f
        val isCoarseProvider = provider.equals("network", ignoreCase = true) || candidateAccuracy > 50f

        if (previousAnchored != null && isCoarseProvider && (nowMs - lastAccurateFixMs) < RECENT_ACCURATE_WINDOW_MS) {
            return StabilizedLocationResult(
                location = previousAnchored.copy(speedMps = 0f),
                lastAccurateFixMs = lastAccurateFixMs,
                moved = false
            )
        }

        val rawSpeed = candidate.speedMps ?: 0f
        val effectiveSpeed = if (rawSpeed < STATIONARY_SPEED_THRESHOLD_MPS) 0f else rawSpeed
        val isAccurateSource = !isCoarseProvider && candidateAccuracy <= 30f
        val nextAccurateMs = if (isAccurateSource) nowMs else lastAccurateFixMs

        if (previousAnchored == null) {
            return StabilizedLocationResult(
                location = candidate.copy(speedMps = effectiveSpeed),
                lastAccurateFixMs = nextAccurateMs,
                moved = true
            )
        }

        val distanceMeters = HaversineDistanceCalculator.distanceMeters(previousAnchored, candidate)
        val noiseRadiusMeters = maxOf(8.0, candidateAccuracy * 1.15).coerceAtMost(22.0)

        // Si el vehículo está parado o el desplazamiento entra dentro del círculo de ruido GNSS, anclar el cursor
        val isStationaryDrift =
            (rawSpeed < STATIONARY_SPEED_THRESHOLD_MPS && distanceMeters < noiseRadiusMeters) ||
                (rawSpeed < 0.8f && candidateAccuracy > 15f && distanceMeters < 30.0)

        if (isStationaryDrift) {
            val anchored = previousAnchored.copy(
                timestamp = candidate.timestamp,
                speedMps = 0f,
                accuracyMeters = candidate.accuracyMeters
            )
            return StabilizedLocationResult(
                location = anchored,
                lastAccurateFixMs = nextAccurateMs,
                moved = false
            )
        }

        val nextBearing = if (rawSpeed >= STATIONARY_SPEED_THRESHOLD_MPS) {
            candidate.bearingDegrees ?: previousAnchored.bearingDegrees
        } else {
            previousAnchored.bearingDegrees
        }

        return StabilizedLocationResult(
            location = candidate.copy(
                speedMps = effectiveSpeed,
                bearingDegrees = nextBearing
            ),
            lastAccurateFixMs = nextAccurateMs,
            moved = true
        )
    }
}
