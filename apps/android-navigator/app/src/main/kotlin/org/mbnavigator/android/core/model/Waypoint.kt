package org.mbnavigator.android.core.model

import java.util.UUID

/**
 * Punto de interés (POI) o parada intermedia obligatoria en una ruta.
 */
data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val point: GeoPoint,
    val description: String? = null,
    val symbol: String? = null,
    val isMandatoryPassThrough: Boolean = true
)
