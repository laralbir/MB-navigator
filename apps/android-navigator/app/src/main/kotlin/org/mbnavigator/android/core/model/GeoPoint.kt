package org.mbnavigator.android.core.model

import java.time.Instant

/**
 * Representa una coordenada geográfica canónica en MB-navigator.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val timestamp: Instant? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, got: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, got: $longitude" }
    }
}
