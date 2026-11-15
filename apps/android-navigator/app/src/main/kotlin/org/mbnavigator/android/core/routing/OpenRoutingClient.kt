package org.mbnavigator.android.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mbnavigator.android.core.geo.ElevationMetricsCalculator
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Waypoint
import org.mbnavigator.android.core.settings.RouteCalculationOptions
import java.io.OutputStreamWriter
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
     * Calcula una ruta real por carreteras usando Valhalla (`costing: motorcycle`) respetando
     * preferencias reales (autovía, peajes, ferris, caminos sin asfaltar), con respaldo OSRM y offline.
     */
    suspend fun calculateRoadRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        viaPoints: List<Waypoint> = emptyList(),
        profile: MotorcycleRoutingProfile = MotorcycleRoutingProfile.CURVY,
        options: RouteCalculationOptions = RouteCalculationOptions()
    ): CalculatedRouteResult = withContext(Dispatchers.IO) {
        val allStops = buildList {
            add(origin)
            viaPoints.forEach { add(it.point) }
            add(destination)
        }

        // 1) Motor Valhalla (soporta costing=motorcycle + use_highways/tolls/ferry/trails)
        tryValhallaMotorcycleRoute(allStops, destinationName, profile, options)?.let {
            return@withContext it
        }

        // 2) Fallback OSRM público si Valhalla no responde
        try {
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

        // 3) Fallback offline cuando no hay red disponible
        val distMeters = HaversineDistanceCalculator.totalDistanceMeters(allStops)
        val maneuvers = TurnByTurnEngine.extractManeuversFromTrack(allStops, destinationName)
        CalculatedRouteResult(
            title = "Ruta Directa (Offline) a $destinationName",
            points = allStops,
            maneuvers = maneuvers,
            distanceKm = distMeters / 1000.0,
            estimatedMinutes = ((distMeters / 1000.0) / 65.0 * 60.0).toInt().coerceAtLeast(1),
            isOnlineRoute = false
        )
    }

    /**
     * Determina si un trazado cargado (p. ej. GPX de pocos puntos o ruta predefinida)
     * tiene segmentos rectos largos que conviene ajustar suavemente a las carreteras reales del mapa.
     */
    fun needsRoadSnapping(points: List<GeoPoint>): Boolean {
        if (points.size < 2) return false
        var totalMeters = 0.0
        var maxSegMeters = 0.0
        for (i in 0 until points.lastIndex) {
            val d = HaversineDistanceCalculator.distanceMeters(points[i], points[i + 1])
            totalMeters += d
            if (d > maxSegMeters) maxSegMeters = d
        }
        val avgSegMeters = totalMeters / (points.size - 1)
        return avgSegMeters > 95.0 || maxSegMeters > 450.0
    }

    /**
     * Ajusta un track o lista de puntos dispersos a la geometría exacta de carreteras OpenStreetMap
     * usando Valhalla (`trace_route` con `map_snap`, o ruta multistop como respaldo).
     */
    suspend fun snapRouteToRoads(
        points: List<GeoPoint>,
        routeTitle: String,
        viaPoints: List<Waypoint> = emptyList(),
        profile: MotorcycleRoutingProfile = MotorcycleRoutingProfile.CURVY,
        options: RouteCalculationOptions = RouteCalculationOptions()
    ): CalculatedRouteResult? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null

        // Si son pocos puntos de paso (< 32), Valhalla route calcula el trazado exacto por carretera entre ellos
        if (points.size <= 32) {
            tryValhallaMotorcycleRoute(points, routeTitle, profile, options)?.let { snapped ->
                return@withContext snapped.copy(title = routeTitle)
            }
        }

        // Para tracks más largos, map-matching Valhalla /trace_route (máx. 180 muestras)
        val sampled = downsampleEvenly(points, 180)
        tryValhallaTraceMatch(sampled, routeTitle, profile, options)?.let {
            return@withContext it
        }

        // Respaldo: muestrear hasta 20 puntos de paso y enrutar
        val keyStops = downsampleEvenly(points, 20)
        tryValhallaMotorcycleRoute(keyStops, routeTitle, profile, options)?.let { snapped ->
            return@withContext snapped.copy(title = routeTitle)
        }
        null
    }

    private fun downsampleEvenly(points: List<GeoPoint>, maxCount: Int): List<GeoPoint> {
        if (points.size <= maxCount) return points
        val out = ArrayList<GeoPoint>(maxCount)
        val last = points.lastIndex.toDouble()
        for (i in 0 until maxCount) {
            val idx = ((i.toDouble() / (maxCount - 1)) * last).toInt().coerceIn(0, points.lastIndex)
            val candidate = points[idx]
            if (out.isEmpty() || out.last() != candidate) out.add(candidate)
        }
        if (out.last() != points.last()) out[out.lastIndex] = points.last()
        return out
    }

    private fun buildMotorcycleCostingOptions(
        profile: MotorcycleRoutingProfile,
        options: RouteCalculationOptions
    ): JSONObject {
        val highwayPref = when {
            !options.allowHighways -> 0.0
            profile == MotorcycleRoutingProfile.FAST_HIGHWAY -> 1.0
            profile == MotorcycleRoutingProfile.EXTRA_CURVY_SCENIC -> 0.15
            else -> 0.35
        }
        return JSONObject().apply {
            put("use_highways", highwayPref)
            put("use_tolls", if (options.allowTolls) 0.9 else 0.0)
            put("use_ferry", if (options.allowFerries) 0.85 else 0.0)
            put("use_trails", if (options.allowUnpavedTracks) 0.85 else 0.0)
        }
    }

    private fun tryValhallaTraceMatch(
        tracePoints: List<GeoPoint>,
        routeTitle: String,
        profile: MotorcycleRoutingProfile,
        options: RouteCalculationOptions
    ): CalculatedRouteResult? {
        return try {
            val shapeArr = org.json.JSONArray()
            tracePoints.forEach { pt ->
                shapeArr.put(
                    JSONObject().apply {
                        put("lat", pt.latitude)
                        put("lon", pt.longitude)
                    }
                )
            }
            val payload = JSONObject().apply {
                put("shape", shapeArr)
                put("costing", "motorcycle")
                put("costing_options", JSONObject().apply {
                    put("motorcycle", buildMotorcycleCostingOptions(profile, options))
                })
                put("shape_match", "map_snap")
                put("language", "es-ES")
                put("directions_options", JSONObject().apply { put("units", "kilometers") })
            }
            val conn = (URL("https://valhalla1.openstreetmap.de/trace_route").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 9000
                readTimeout = 9000
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseValhallaTripJson(body, routeTitle)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryValhallaMotorcycleRoute(
        stops: List<GeoPoint>,
        destinationName: String,
        profile: MotorcycleRoutingProfile,
        options: RouteCalculationOptions
    ): CalculatedRouteResult? {
        return try {
            val locationsArr = org.json.JSONArray()
            stops.forEach { pt ->
                locationsArr.put(
                    JSONObject().apply {
                        put("lat", pt.latitude)
                        put("lon", pt.longitude)
                    }
                )
            }

            val motoOpts = buildMotorcycleCostingOptions(profile, options)

            val payload = JSONObject().apply {
                put("locations", locationsArr)
                put("costing", "motorcycle")
                put("costing_options", JSONObject().apply { put("motorcycle", motoOpts) })
                put("language", "es-ES")
                put("directions_options", JSONObject().apply { put("units", "kilometers") })
            }

            val conn = (URL("https://valhalla1.openstreetmap.de/route").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 9000
                readTimeout = 9000
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseValhallaTripJson(body, "Ruta a $destinationName")
        } catch (_: Exception) {
            null
        }
    }

    private fun parseValhallaTripJson(body: String, resultTitle: String): CalculatedRouteResult? {
        return try {
            val trip = JSONObject(body).optJSONObject("trip") ?: return null
            val legs = trip.optJSONArray("legs") ?: return null
            if (legs.length() == 0) return null

            val combinedPoints = mutableListOf<GeoPoint>()
            val combinedManeuvers = mutableListOf<RouteManeuver>()
            var totalDistKm = 0.0
            var totalDurationSec = 0.0

            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                val summary = leg.optJSONObject("summary")
                totalDistKm += summary?.optDouble("length", 0.0) ?: 0.0
                totalDurationSec += summary?.optDouble("time", 0.0) ?: 0.0

                val shapeStr = leg.optString("shape", "")
                val legPoints = decodePolyline6(shapeStr)
                val pointOffset = if (combinedPoints.isEmpty()) 0 else (combinedPoints.size - 1)
                if (combinedPoints.isEmpty()) {
                    combinedPoints.addAll(legPoints)
                } else if (legPoints.size > 1) {
                    combinedPoints.addAll(legPoints.drop(1))
                }

                val maneuversArr = leg.optJSONArray("maneuvers") ?: continue
                for (m in 0 until maneuversArr.length()) {
                    val mObj = maneuversArr.getJSONObject(m)
                    val beginIdx = (pointOffset + mObj.optInt("begin_shape_index", 0))
                        .coerceIn(0, (combinedPoints.size - 1).coerceAtLeast(0))
                    val loc = combinedPoints.getOrElse(beginIdx) { combinedPoints.firstOrNull() ?: return null }
                    val valType = mObj.optInt("type", 0)
                    val mType = mapValhallaManeuverType(valType)
                    val streetArr = mObj.optJSONArray("street_names")
                    val streetName = if (streetArr != null && streetArr.length() > 0) streetArr.optString(0, "") else ""
                    val instruction = mObj.optString("instruction", "Continúa").trim()
                    val distCumulative = TurnByTurnEngine.computeCumulativeDistances(combinedPoints)
                        .getOrElse(beginIdx) { 0.0 }

                    combinedManeuvers.add(
                        RouteManeuver(
                            index = combinedManeuvers.size,
                            pointIndex = beginIdx,
                            location = loc,
                            type = mType,
                            instruction = instruction,
                            roadName = streetName,
                            distanceFromStartMeters = distCumulative
                        )
                    )
                }
            }

            if (combinedPoints.size < 2) return null

            CalculatedRouteResult(
                title = resultTitle,
                points = combinedPoints,
                maneuvers = combinedManeuvers.ifEmpty {
                    TurnByTurnEngine.extractManeuversFromTrack(combinedPoints, resultTitle)
                },
                distanceKm = if (totalDistKm > 0.0) totalDistKm else HaversineDistanceCalculator.totalDistanceMeters(combinedPoints) / 1000.0,
                estimatedMinutes = (totalDurationSec / 60.0).toInt().coerceAtLeast(1),
                isOnlineRoute = true
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun decodePolyline6(encoded: String): List<GeoPoint> {
        if (encoded.isEmpty()) return emptyList()
        val points = mutableListOf<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lon = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dLat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLon = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lon += dLon

            points.add(GeoPoint(lat / 1_000_000.0, lon / 1_000_000.0))
        }
        return points
    }

    private fun mapValhallaManeuverType(typeCode: Int): ManeuverType {
        return when (typeCode) {
            1, 2, 3 -> ManeuverType.DEPART
            4, 5, 6 -> ManeuverType.ARRIVE
            10 -> ManeuverType.RIGHT
            9 -> ManeuverType.SLIGHT_RIGHT
            11 -> ManeuverType.SHARP_RIGHT
            15 -> ManeuverType.LEFT
            16 -> ManeuverType.SLIGHT_LEFT
            14 -> ManeuverType.SHARP_LEFT
            12, 13 -> ManeuverType.U_TURN
            26, 27 -> ManeuverType.ROUNDABOUT
            else -> ManeuverType.STRAIGHT
        }
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
