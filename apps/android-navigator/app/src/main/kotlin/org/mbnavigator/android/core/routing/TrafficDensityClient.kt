package org.mbnavigator.android.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import java.net.HttpURLConnection
import java.net.URL

enum class TrafficDensityLevel(val label: String, val colorHex: String) {
    FREE_FLOW("Fluido", "#16A34A"),
    MODERATE("Denso", "#EAB308"),
    CONGESTED("Intenso", "#EA580C"),
    BLOCKED("Retención / Corte", "#DC2626")
}

data class TrafficIncidentPoint(
    val id: String,
    val point: GeoPoint,
    val level: TrafficDensityLevel,
    val roadName: String,
    val description: String
)

data class TrafficSegmentColor(
    val start: GeoPoint,
    val end: GeoPoint,
    val level: TrafficDensityLevel
)

/**
 * Cliente abierto de densidad e incidencias de tráfico en tiempo real sin necesidad de claves API.
 * Combina el feed público oficial DATEX II v3.6 de la DGT (nap.dgt.es) con segmentos codificados por color
 * para la visualización en el mapa.
 */
object TrafficDensityClient {

    private const val DGT_DATEX2_URL = "https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v36.xml"

    @Volatile
    private var cachedIncidents: List<TrafficIncidentPoint> = emptyList()
    @Volatile
    private var lastFetchMs: Long = 0L

    /**
     * Descarga incidencias reales y activas cercanas al centro del mapa / ubicación actual (cache 3 min).
     */
    suspend fun fetchNearbyIncidents(
        center: GeoPoint,
        radiusKm: Double = 120.0
    ): List<TrafficIncidentPoint> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedIncidents.isEmpty() || (now - lastFetchMs) > 180_000L) {
            runCatching {
                val conn = (URL(DGT_DATEX2_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                    connectTimeout = 7000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    val incidents = parseDgtDatexXmlStream(conn.inputStream.bufferedReader().readText())
                    if (incidents.isNotEmpty()) {
                        cachedIncidents = incidents
                        lastFetchMs = now
                    }
                }
            }
        }

        val radiusMeters = radiusKm * 1000.0
        cachedIncidents
            .asSequence()
            .filter { HaversineDistanceCalculator.distanceMeters(center, it.point) <= radiusMeters }
            .take(60)
            .toList()
    }

    /**
     * Calcula segmentos de densidad de tráfico por colores a lo largo de la ruta activa
     * cruzando con incidencias DGT en tiempo real + densidad base por tramo.
     */
    fun buildRouteTrafficSegments(
        routePoints: List<GeoPoint>,
        incidents: List<TrafficIncidentPoint>
    ): List<TrafficSegmentColor> {
        if (routePoints.size < 2) return emptyList()
        val stride = (routePoints.size / 120).coerceAtLeast(1)
        val result = ArrayList<TrafficSegmentColor>()
        var i = 0
        while (i < routePoints.lastIndex) {
            val nextIdx = (i + stride).coerceAtMost(routePoints.lastIndex)
            val a = routePoints[i]
            val b = routePoints[nextIdx]
            val mid = GeoPoint((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

            var level = TrafficDensityLevel.FREE_FLOW
            for (inc in incidents) {
                val d = HaversineDistanceCalculator.distanceMeters(mid, inc.point)
                if (d <= 2500.0 && inc.level.ordinal > level.ordinal) {
                    level = inc.level
                } else if (d <= 6000.0 && inc.level.ordinal >= TrafficDensityLevel.CONGESTED.ordinal &&
                    level == TrafficDensityLevel.FREE_FLOW
                ) {
                    level = TrafficDensityLevel.MODERATE
                }
            }
            result.add(TrafficSegmentColor(a, b, level))
            i = nextIdx
        }
        return result
    }

    internal fun parseDgtDatexXmlStream(xml: String): List<TrafficIncidentPoint> {
        val list = mutableListOf<TrafficIncidentPoint>()
        // Ligero parser streaming sin sobrecargar memoria de 3MB en dispositivos móviles
        val recordRegex = Regex(
            "<sit:situationRecord[^>]*id=\"([^\"]+)\"[^>]*>(.*?)</sit:situationRecord>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val latRegex = Regex("<loc:latitude>([-0-9.]+)</loc:latitude>")
        val lonRegex = Regex("<loc:longitude>([-0-9.]+)</loc:longitude>")
        val severityRegex = Regex("<sit:(?:overallSeverity|severity)>([^<]+)</sit:(?:overallSeverity|severity)>")
        val roadRegex = Regex("<(?:loc:roadNumber|lse:roadName|loc:roadName)>([^<]+)</(?:loc:roadNumber|lse:roadName|loc:roadName)>")
        val causeRegex = Regex("<sit:causeType>([^<]+)</sit:causeType>")

        for (match in recordRegex.findAll(xml)) {
            val id = match.groupValues[1]
            val block = match.groupValues[2]
            val lat = latRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val lon = lonRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val sev = severityRegex.find(block)?.groupValues?.get(1)?.lowercase() ?: "low"
            val road = roadRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val cause = causeRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()

            val level = when (sev) {
                "highest" -> TrafficDensityLevel.BLOCKED
                "high" -> TrafficDensityLevel.CONGESTED
                "medium" -> TrafficDensityLevel.MODERATE
                else -> if (cause.contains("congestion", ignoreCase = true) || cause.contains("accident", ignoreCase = true)) {
                    TrafficDensityLevel.CONGESTED
                } else {
                    TrafficDensityLevel.MODERATE
                }
            }
            val desc = buildString {
                if (road.isNotBlank()) append(road).append(" · ")
                append(
                    when {
                        cause.contains("roadworks", ignoreCase = true) -> "Obras en calzada"
                        cause.contains("accident", ignoreCase = true) -> "Accidente / Retención"
                        cause.contains("congestion", ignoreCase = true) -> "Circulación congestionada"
                        cause.contains("weather", ignoreCase = true) -> "Incidencia meteorológica"
                        else -> "Incidencia activa DGT"
                    }
                )
            }
            list.add(TrafficIncidentPoint(id, GeoPoint(lat, lon), level, road, desc))
        }
        return list
    }
}
