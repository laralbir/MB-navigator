package org.mbnavigator.android.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mbnavigator.android.core.geo.ElevationMetricsCalculator
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Waypoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class GeocodedPlace(
    val displayName: String,
    val shortName: String,
    val location: GeoPoint
)

data class CalculatedRouteResult(
    val title: String,
    val points: List<GeoPoint>,
    val maneuvers: List<RouteManeuver>,
    val distanceKm: Double,
    val estimatedMinutes: Int,
    val isOnlineRoute: Boolean
)

object OpenRoutingClient {

    suspend fun searchDestination(query: String): Result<List<GeocodedPlace>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encoded&format=jsonv2&limit=6&accept-language=es"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(body)
            val places = mutableListOf<GeocodedPlace>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val lat = obj.getString("lat").toDouble()
                val lon = obj.getString("lon").toDouble()
                val full = obj.getString("display_name")
                val short = obj.optString("name").ifBlank { full.substringBefore(",") }
                places.add(GeocodedPlace(full, short, GeoPoint(lat, lon)))
            }
            Result.success(places)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calcula una ruta real por carreteras usando OSRM abierto con instrucciones paso a paso,
     * waypoints intermedios obligatorios y fallback geométrico offline si no hay conexión.
     */
    suspend fun calculateRoadRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        viaPoints: List<Waypoint> = emptyList(),
        profile: MotorcycleRoutingProfile = MotorcycleRoutingProfile.CURVY
    ): CalculatedRouteResult = withContext(Dispatchers.IO) {
        try {
            val allStops = buildList {
                add(origin)
                viaPoints.forEach { add(it.point) }
                add(destination)
            }
            val coordsParam = allStops.joinToString(";") { "${it.longitude},${it.latitude}" }
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson&steps=true"

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                connectTimeout = 9000
                readTimeout = 9000
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("code") == "Ok") {
                    val routeObj = json.getJSONArray("routes").getJSONObject(0)
                    val totalDistMeters = routeObj.getDouble("distance")
                    val durationSeconds = routeObj.getDouble("duration")

                    val geometryCoords = routeObj.getJSONObject("geometry").getJSONArray("coordinates")
                    val trackPoints = ArrayList<GeoPoint>(geometryCoords.length())
                    for (i in 0 until geometryCoords.length()) {
                        val c = geometryCoords.getJSONArray(i)
                        trackPoints.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
                    }

                    val maneuvers = parseOsrmManeuvers(routeObj, trackPoints, destinationName)

                    return@withContext CalculatedRouteResult(
                        title = "Ruta a $destinationName",
                        points = trackPoints,
                        maneuvers = maneuvers,
                        distanceKm = totalDistMeters / 1000.0,
                        estimatedMinutes = (durationSeconds / 60.0).toInt().coerceAtLeast(1),
                        isOnlineRoute = true
                    )
                }
            }
        } catch (_: Exception) {
            // Caída limpia a cálculo offline si no hay red
        }

        // Fallback offline cuando no hay red disponible
        val fallbackPoints = buildList {
            add(origin)
            viaPoints.forEach { add(it.point) }
            add(destination)
        }
        val distMeters = HaversineDistanceCalculator.totalDistanceMeters(fallbackPoints)
        val maneuvers = TurnByTurnEngine.extractManeuversFromTrack(fallbackPoints, destinationName)
        CalculatedRouteResult(
            title = "Ruta Directa (Offline) a $destinationName",
            points = fallbackPoints,
            maneuvers = maneuvers,
            distanceKm = distMeters / 1000.0,
            estimatedMinutes = ((distMeters / 1000.0) / 65.0 * 60.0).toInt().coerceAtLeast(1),
            isOnlineRoute = false
        )
    }

    private fun parseOsrmManeuvers(
        routeObj: JSONObject,
        trackPoints: List<GeoPoint>,
        destinationName: String
    ): List<RouteManeuver> {
        val legs = routeObj.optJSONArray("legs") ?: return TurnByTurnEngine.extractManeuversFromTrack(trackPoints, destinationName)
        val cumulative = TurnByTurnEngine.computeCumulativeDistances(trackPoints)
        val result = mutableListOf<RouteManeuver>()

        var runningDist = 0.0
        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val steps = leg.optJSONArray("steps") ?: continue
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val stepDist = step.optDouble("distance", 0.0)
                val roadName = step.optString("name", "").trim()
                val ref = step.optString("ref", "").trim()
                val finalRoad = when {
                    ref.isNotBlank() && roadName.isNotBlank() -> "$ref · $roadName"
                    ref.isNotBlank() -> ref
                    else -> roadName
                }

                val maneuverObj = step.getJSONObject("maneuver")
                val osrmType = maneuverObj.optString("type", "turn")
                val modifier = maneuverObj.optString("modifier", "")
                val exitNum = maneuverObj.optInt("exit", 0)
                val locArr = maneuverObj.getJSONArray("location")
                val loc = GeoPoint(locArr.getDouble(1), locArr.getDouble(0))

                // Buscar punto más cercano en trackPoints para indexación consistente
                var bestIdx = 0
                var minD = Double.MAX_VALUE
                for (pi in trackPoints.indices) {
                    val d = HaversineDistanceCalculator.distanceMeters(loc, trackPoints[pi])
                    if (d < minD) {
                        minD = d
                        bestIdx = pi
                    }
                }

                val (mType, text) = translateOsrmManeuver(osrmType, modifier, exitNum, finalRoad, destinationName)
                result.add(
                    RouteManeuver(
                        index = result.size,
                        pointIndex = bestIdx,
                        location = loc,
                        type = mType,
                        instruction = text,
                        roadName = finalRoad,
                        distanceFromStartMeters = cumulative.getOrElse(bestIdx) { runningDist }
                    )
                )
                runningDist += stepDist
            }
        }
        return result.ifEmpty { TurnByTurnEngine.extractManeuversFromTrack(trackPoints, destinationName) }
    }

    private fun translateOsrmManeuver(
        type: String,
        modifier: String,
        exit: Int,
        roadName: String,
        destName: String
    ): Pair<ManeuverType, String> {
        val mType = when (type) {
            "depart" -> ManeuverType.DEPART
            "arrive" -> ManeuverType.ARRIVE
            "roundabout", "rotary", "roundabout turn" -> ManeuverType.ROUNDABOUT
            else -> when (modifier) {
                "uturn" -> ManeuverType.U_TURN
                "sharp right" -> ManeuverType.SHARP_RIGHT
                "right" -> ManeuverType.RIGHT
                "slight right" -> ManeuverType.SLIGHT_RIGHT
                "sharp left" -> ManeuverType.SHARP_LEFT
                "left" -> ManeuverType.LEFT
                "slight left" -> ManeuverType.SLIGHT_LEFT
                else -> ManeuverType.STRAIGHT
            }
        }

        val instruction = when (type) {
            "depart" -> if (roadName.isNotBlank()) "Sal por $roadName" else "Inicia la marcha"
            "arrive" -> "Llegada a $destName"
            "roundabout", "rotary", "roundabout turn" -> {
                if (exit > 0) "En la rotonda, toma la ${exit}ª salida" else "Toma la rotonda"
            }
            "exit roundabout", "exit rotary" -> "Sal de la rotonda"
            "merge" -> when {
                modifier.contains("left") -> "Incorpórate por la izquierda"
                modifier.contains("right") -> "Incorpórate por la derecha"
                else -> "Incorpórate a la vía"
            }
            "fork" -> when {
                modifier.contains("left") -> "Mantente a la izquierda en el desvío"
                modifier.contains("right") -> "Mantente a la derecha en el desvío"
                else -> "Sigue por la bifurcación"
            }
            "new name", "continue" -> if (roadName.isNotBlank()) "Continúa por $roadName" else "Continúa recto"
            else -> when (mType) {
                ManeuverType.SHARP_RIGHT -> "Curva muy cerrada a la derecha"
                ManeuverType.RIGHT -> "Gira a la derecha"
                ManeuverType.SLIGHT_RIGHT -> "Gira levemente a la derecha"
                ManeuverType.SHARP_LEFT -> "Curva muy cerrada a la izquierda"
                ManeuverType.LEFT -> "Gira a la izquierda"
                ManeuverType.SLIGHT_LEFT -> "Gira levemente a la izquierda"
                ManeuverType.U_TURN -> "Haz cambio de sentido"
                else -> "Sigue recto"
            }
        }
        return mType to instruction
    }
}
