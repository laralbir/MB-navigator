package org.mbnavigator.android.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class TrafficDensityLevel(val label: String, val colorHex: String) {
    FREE_FLOW("Fluido", "#16A34A"),
    MODERATE("Denso", "#EAB308"),
    CONGESTED("Intenso", "#EA580C"),
    BLOCKED("Retención / Corte", "#DC2626")
}

enum class HazardCategory(
    val label: String,
    val spokenPrefix: String,
    val badgeIcon: String,
    val colorHex: String,
    val isCriticalSafetyHazard: Boolean
) {
    SPEED_CAMERA("Radar", "Atención, radar", "📷", "#DC2626", true),
    BROKEN_DOWN_VEHICLE("Vehículo averiado / V16", "Peligro, vehículo averiado", "⚠️", "#EA580C", true),
    ACCIDENT("Accidente en calzada", "Peligro, accidente en calzada", "🚨", "#DC2626", true),
    OBSTACLE("Obstáculo en calzada", "Atención, obstáculo en calzada", "🚧", "#EA580C", true),
    ROAD_CLOSURE("Corte de carretera", "Atención, corte de carretera", "⛔", "#DC2626", true),
    ROADWORK("Obras en calzada", "Precaución, obras en calzada", "🛠️", "#EAB308", true),
    WEATHER_HAZARD("Peligro meteorológico", "Precaución meteorológica", "🌧️", "#0284C7", true),
    CONGESTION("Tráfico denso", "Tráfico denso", "🚦", "#EAB308", false)
}

data class TrafficIncidentPoint(
    val id: String,
    val point: GeoPoint,
    val level: TrafficDensityLevel,
    val roadName: String,
    val description: String,
    val category: HazardCategory = HazardCategory.CONGESTION,
    val speedLimitKmh: Int? = null
)

data class TrafficSegmentColor(
    val points: List<GeoPoint>,
    val level: TrafficDensityLevel
) {
    val start: GeoPoint get() = points.first()
    val end: GeoPoint get() = points.last()
}

/**
 * Cliente abierto de densidad, incidencias reales DGT 3.0 y radares OSM sin claves API.
 * Combina el feed público DATEX II v3.7 de la DGT (nap.dgt.es) y nodos OSM de radar.
 */
object TrafficDensityClient {

    private const val DGT_DATEX2_URL = "https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v37.xml"
    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    @Volatile
    private var cachedIncidents: List<TrafficIncidentPoint> = emptyList()
    @Volatile
    private var lastFetchMs: Long = 0L

    @Volatile
    private var cachedCameras: List<TrafficIncidentPoint> = emptyList()
    @Volatile
    private var lastCameraFetchCenter: GeoPoint? = null
    @Volatile
    private var lastCameraFetchMs: Long = 0L

    /**
     * Descarga incidencias reales DGT y radares públicos cercanos al centro / ubicación actual.
     */
    suspend fun fetchNearbyIncidents(
        center: GeoPoint,
        radiusKm: Double = 120.0,
        includeSpeedCameras: Boolean = true
    ): List<TrafficIncidentPoint> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedIncidents.isEmpty() || (now - lastFetchMs) > 180_000L) {
            runCatching {
                val conn = (URL(DGT_DATEX2_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
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

        if (includeSpeedCameras) {
            val prevCenter = lastCameraFetchCenter
            val movedFar = prevCenter == null ||
                HaversineDistanceCalculator.distanceMeters(prevCenter, center) > 35_000.0
            if (cachedCameras.isEmpty() || movedFar || (now - lastCameraFetchMs) > 900_000L) {
                fetchOsmSpeedCameras(center, radiusMeters = (radiusKm * 1000.0).toInt().coerceIn(15_000, 80_000))
                    .takeIf { it.isNotEmpty() }
                    ?.let { cams ->
                        cachedCameras = cams
                        lastCameraFetchCenter = center
                        lastCameraFetchMs = now
                    }
            }
        }

        val radiusMeters = radiusKm * 1000.0
        val dgtNearby = cachedIncidents
            .asSequence()
            .filter { HaversineDistanceCalculator.distanceMeters(center, it.point) <= radiusMeters }
            .sortedByDescending { it.category.isCriticalSafetyHazard }
            .take(65)
            .toList()

        val camNearby = if (includeSpeedCameras) {
            cachedCameras
                .asSequence()
                .filter { HaversineDistanceCalculator.distanceMeters(center, it.point) <= radiusMeters }
                .take(40)
                .toList()
        } else {
            emptyList()
        }

        dgtNearby + camNearby
    }

    private fun fetchOsmSpeedCameras(center: GeoPoint, radiusMeters: Int): List<TrafficIncidentPoint> {
        val query = "[out:json][timeout:6];node[\"highway\"=\"speed_camera\"](around:$radiusMeters,${center.latitude},${center.longitude});out body 80;"
        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 6000
                }
                conn.outputStream.bufferedWriter().use {
                    it.write("data=${URLEncoder.encode(query, "UTF-8")}")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseOverpassSpeedCamerasJson(body)
                    if (parsed.isNotEmpty()) return parsed
                }
            } catch (_: Exception) {
                // Intentar siguiente réplica Overpass si está saturada
            }
        }
        return emptyList()
    }

    internal fun parseOverpassSpeedCamerasJson(jsonStr: String): List<TrafficIncidentPoint> {
        return runCatching {
            val elements = JSONObject(jsonStr).optJSONArray("elements") ?: return emptyList()
            val list = mutableListOf<TrafficIncidentPoint>()
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val lat = el.optDouble("lat", Double.NaN)
                val lon = el.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val tags = el.optJSONObject("tags")
                val maxSpeed = tags?.optString("maxspeed")?.filter { it.isDigit() }?.toIntOrNull()
                val refRoad = tags?.optString("ref")?.ifBlank { tags.optString("name", "") }.orEmpty()
                val speedDesc = if (maxSpeed != null) "Radar $maxSpeed km/h" else "Radar de velocidad"
                val desc = if (refRoad.isNotBlank()) "$refRoad · $speedDesc" else speedDesc
                list.add(
                    TrafficIncidentPoint(
                        id = "cam-${el.optLong("id", i.toLong())}",
                        point = GeoPoint(lat, lon),
                        level = TrafficDensityLevel.BLOCKED,
                        roadName = refRoad,
                        description = desc,
                        category = HazardCategory.SPEED_CAMERA,
                        speedLimitKmh = maxSpeed
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Calcula segmentos de densidad de tráfico por colores a lo largo de la ruta activa
     * conservando TODOS los vértices intermedios para seguir con exactitud cada curva del trazado.
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
            val subPoints = routePoints.subList(i, nextIdx + 1).toList()
            val mid = subPoints[subPoints.size / 2]

            var level = TrafficDensityLevel.FREE_FLOW
            for (inc in incidents) {
                if (inc.category == HazardCategory.SPEED_CAMERA) continue
                val d = HaversineDistanceCalculator.distanceMeters(mid, inc.point)
                if (d <= 2500.0 && inc.level.ordinal > level.ordinal) {
                    level = inc.level
                } else if (d <= 6000.0 && inc.level.ordinal >= TrafficDensityLevel.CONGESTED.ordinal &&
                    level == TrafficDensityLevel.FREE_FLOW
                ) {
                    level = TrafficDensityLevel.MODERATE
                }
            }
            result.add(TrafficSegmentColor(subPoints, level))
            i = nextIdx
        }
        return result
    }

    internal fun parseDgtDatexXmlStream(xml: String): List<TrafficIncidentPoint> {
        val list = mutableListOf<TrafficIncidentPoint>()
        val recordRegex = Regex(
            "<sit:situationRecord([^>]*)>(.*?)</sit:situationRecord>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val idAttrRegex = Regex("id=\"([^\"]+)\"")
        val xsiTypeRegex = Regex("xsi:type=\"([^\"]+)\"")
        val latRegex = Regex("<loc:latitude>([-0-9.]+)</loc:latitude>")
        val lonRegex = Regex("<loc:longitude>([-0-9.]+)</loc:longitude>")
        val severityRegex = Regex("<sit:(?:overallSeverity|severity)>([^<]+)</sit:(?:overallSeverity|severity)>")
        val roadRegex = Regex("<(?:loc:roadNumber|lse:roadName|loc:roadName)>([^<]+)</(?:loc:roadNumber|lse:roadName|loc:roadName)>")
        val causeRegex = Regex("<sit:causeType>([^<]+)</sit:causeType>")
        val vehObstRegex = Regex("<sit:vehicleObstructionType>([^<]+)</sit:vehicleObstructionType>")
        val laneUsageRegex = Regex("<loc:laneUsage>([^<]+)</loc:laneUsage>")

        for (match in recordRegex.findAll(xml)) {
            val attrs = match.groupValues[1]
            val block = match.groupValues[2]
            val id = idAttrRegex.find(attrs)?.groupValues?.get(1) ?: continue
            val recordType = xsiTypeRegex.find(attrs)?.groupValues?.get(1).orEmpty()
            val lat = latRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val lon = lonRegex.find(block)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            val sev = severityRegex.find(block)?.groupValues?.get(1)?.lowercase() ?: "low"
            val road = roadRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val cause = causeRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val vehType = vehObstRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val laneUsage = laneUsageRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()

            val category = when {
                cause.contains("accident", ignoreCase = true) ||
                    recordType.contains("Accident", ignoreCase = true) -> HazardCategory.ACCIDENT

                cause.contains("vehicleObstruction", ignoreCase = true) ||
                    vehType.contains("vehicle", ignoreCase = true) ||
                    vehType.contains("broken", ignoreCase = true) -> HazardCategory.BROKEN_DOWN_VEHICLE

                recordType.contains("RoadOrCarriagewayOrLaneManagement", ignoreCase = true) &&
                    laneUsage.contains("allLanesCompleteCarriageway", ignoreCase = true) -> HazardCategory.ROAD_CLOSURE

                cause.contains("roadMaintenance", ignoreCase = true) ||
                    cause.contains("roadworks", ignoreCase = true) -> HazardCategory.ROADWORK

                cause.contains("poorEnvironment", ignoreCase = true) ||
                    cause.contains("environmental", ignoreCase = true) ||
                    cause.contains("weather", ignoreCase = true) -> HazardCategory.WEATHER_HAZARD

                cause.contains("obstruction", ignoreCase = true) -> HazardCategory.OBSTACLE

                else -> HazardCategory.CONGESTION
            }

            val level = when (sev) {
                "highest" -> TrafficDensityLevel.BLOCKED
                "high" -> TrafficDensityLevel.CONGESTED
                "medium" -> TrafficDensityLevel.MODERATE
                else -> when (category) {
                    HazardCategory.ACCIDENT, HazardCategory.ROAD_CLOSURE -> TrafficDensityLevel.BLOCKED
                    HazardCategory.BROKEN_DOWN_VEHICLE, HazardCategory.OBSTACLE -> TrafficDensityLevel.CONGESTED
                    else -> TrafficDensityLevel.MODERATE
                }
            }

            val desc = buildString {
                if (road.isNotBlank()) append(road).append(" · ")
                append(category.label)
            }
            list.add(
                TrafficIncidentPoint(
                    id = id,
                    point = GeoPoint(lat, lon),
                    level = level,
                    roadName = road,
                    description = desc,
                    category = category
                )
            )
        }
        return list
    }
}
