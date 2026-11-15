package org.mbnavigator.android.core.routing

import org.json.JSONObject
import org.mbnavigator.android.core.geo.ElevationMetricsCalculator
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Waypoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class ParsedRouteData(
    val title: String,
    val format: String,
    val points: List<GeoPoint>,
    val waypoints: List<Waypoint>,
    val distanceKm: Double,
    val elevationGainMeters: Double,
    val curveCount: Int
)

object GpxKmlParser {

    fun parse(rawContent: String, fallbackTitle: String): ParsedRouteData {
        val trimmed = rawContent.trim()
        return when {
            trimmed.startsWith("{") -> parseGeoJson(trimmed, fallbackTitle)
            trimmed.contains("<kml", ignoreCase = true) -> parseKml(trimmed, fallbackTitle)
            else -> parseGpx(trimmed, fallbackTitle)
        }
    }

    private fun parseGpx(xml: String, fallbackTitle: String): ParsedRouteData {
        val trackPoints = mutableListOf<GeoPoint>()
        val routePoints = mutableListOf<GeoPoint>()
        val waypoints = mutableListOf<Waypoint>()
        var trackName: String? = null

        var inTrkpt = false
        var inRtept = false
        var inWpt = false

        var trkLat: Double? = null
        var trkLon: Double? = null
        var trkEle: Double? = null

        var rteLat: Double? = null
        var rteLon: Double? = null
        var rteEle: Double? = null
        var rteName: String? = null
        var rteIsMandatory = true
        var rteInserted = false
        var hasGpxxRpt = false

        var wptLat: Double? = null
        var wptLon: Double? = null
        var wptEle: Double? = null
        var wptName: String? = null

        val textBuffer = StringBuilder()

        fun flushPendingRtept() {
            if (inRtept && !rteInserted && rteLat != null && rteLon != null) {
                val pt = GeoPoint(rteLat!!, rteLon!!, rteEle)
                routePoints.add(pt)
                if (!rteName.isNullOrBlank()) {
                    waypoints.add(
                        Waypoint(
                            name = rteName!!,
                            point = pt,
                            isMandatoryPassThrough = rteIsMandatory
                        )
                    )
                }
                rteInserted = true
            }
        }

        val saxFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val saxParser = saxFactory.newSAXParser()

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                val name = qName.lowercase()
                textBuffer.clear()
                when {
                    name == "trkpt" -> {
                        inTrkpt = true
                        trkLat = attributes.getValue("lat")?.toDoubleOrNull()
                        trkLon = attributes.getValue("lon")?.toDoubleOrNull()
                        trkEle = null
                    }
                    name == "rtept" -> {
                        inRtept = true
                        rteLat = attributes.getValue("lat")?.toDoubleOrNull()
                        rteLon = attributes.getValue("lon")?.toDoubleOrNull()
                        rteEle = null
                        rteName = null
                        rteIsMandatory = true
                        rteInserted = false
                    }
                    inRtept && (name == "shapingpoint" || name.endsWith(":shapingpoint")) -> {
                        rteIsMandatory = false
                    }
                    inRtept && (name == "viapoint" || name.endsWith(":viapoint")) -> {
                        rteIsMandatory = true
                    }
                    name == "rpt" || name.endsWith(":rpt") -> {
                        hasGpxxRpt = true
                        flushPendingRtept()
                        val rLat = attributes.getValue("lat")?.toDoubleOrNull()
                        val rLon = attributes.getValue("lon")?.toDoubleOrNull()
                        if (rLat != null && rLon != null) {
                            routePoints.add(GeoPoint(rLat, rLon, null))
                        }
                    }
                    name == "wpt" -> {
                        inWpt = true
                        wptLat = attributes.getValue("lat")?.toDoubleOrNull()
                        wptLon = attributes.getValue("lon")?.toDoubleOrNull()
                        wptEle = null
                        wptName = null
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                textBuffer.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val name = qName.lowercase()
                val text = textBuffer.toString().trim()
                if (text.isNotEmpty()) {
                    when (name) {
                        "name" -> {
                            when {
                                inWpt -> wptName = text
                                inRtept -> rteName = text
                                !inTrkpt && trackName == null -> trackName = text
                            }
                        }
                        "ele" -> {
                            val e = text.toDoubleOrNull()
                            when {
                                inTrkpt -> trkEle = e
                                inRtept && !rteInserted -> rteEle = e
                                inWpt -> wptEle = e
                            }
                        }
                    }
                }
                when (name) {
                    "trkpt" -> {
                        if (inTrkpt && trkLat != null && trkLon != null) {
                            trackPoints.add(GeoPoint(trkLat!!, trkLon!!, trkEle))
                        }
                        inTrkpt = false
                    }
                    "rtept" -> {
                        flushPendingRtept()
                        inRtept = false
                    }
                    "wpt" -> {
                        if (inWpt && wptLat != null && wptLon != null) {
                            val pt = GeoPoint(wptLat!!, wptLon!!, wptEle)
                            waypoints.add(Waypoint(name = wptName ?: "Waypoint", point = pt))
                        }
                        inWpt = false
                    }
                }
                textBuffer.clear()
            }
        }

        saxParser.parse(InputSource(StringReader(xml)), handler)

        val finalPoints = when {
            trackPoints.size >= 2 -> trackPoints
            routePoints.isNotEmpty() -> routePoints
            trackPoints.isNotEmpty() -> trackPoints
            else -> waypoints.map { it.point }
        }
        val gpxFormatLabel = if (hasGpxxRpt) "GPX 1.2" else "GPX 1.1"
        return buildResult(trackName ?: fallbackTitle, gpxFormatLabel, finalPoints, waypoints)
    }

    private fun parseKml(xml: String, fallbackTitle: String): ParsedRouteData {
        val points = mutableListOf<GeoPoint>()
        var docName: String? = null
        val textBuffer = StringBuilder()

        val saxFactory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val saxParser = saxFactory.newSAXParser()

        val handler = object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                textBuffer.clear()
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                textBuffer.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val name = qName.lowercase()
                val text = textBuffer.toString().trim()
                if (text.isNotEmpty()) {
                    if (name == "name" && docName == null) {
                        docName = text
                    } else if (name == "coordinates") {
                        val tokens = text.split(Regex("\\s+"))
                        for (token in tokens) {
                            val parts = token.split(",")
                            if (parts.size >= 2) {
                                val lon = parts[0].toDoubleOrNull()
                                val lat = parts[1].toDoubleOrNull()
                                val ele = if (parts.size >= 3) parts[2].toDoubleOrNull() else null
                                if (lat != null && lon != null) {
                                    points.add(GeoPoint(lat, lon, ele))
                                }
                            }
                        }
                    }
                }
                textBuffer.clear()
            }
        }

        saxParser.parse(InputSource(StringReader(xml)), handler)
        return buildResult(docName ?: fallbackTitle, "KML", points, emptyList())
    }

    private fun parseGeoJson(jsonStr: String, fallbackTitle: String): ParsedRouteData {
        val points = mutableListOf<GeoPoint>()
        var title: String? = null

        val root = JSONObject(jsonStr)
        val features = root.optJSONArray("features")
        if (features != null) {
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                if (title == null) {
                    title = feature.optJSONObject("properties")?.optString("name")?.takeIf { it.isNotBlank() }
                }
                val geom = feature.optJSONObject("geometry") ?: continue
                extractGeoJsonGeometry(geom, points)
            }
        } else if (root.has("geometry")) {
            extractGeoJsonGeometry(root.getJSONObject("geometry"), points)
        } else if (root.has("coordinates")) {
            extractGeoJsonGeometry(root, points)
        }

        return buildResult(title ?: fallbackTitle, "GeoJSON", points, emptyList())
    }

    private fun extractGeoJsonGeometry(geom: JSONObject, out: MutableList<GeoPoint>) {
        val type = geom.optString("type")
        val coords = geom.optJSONArray("coordinates") ?: return
        when (type) {
            "LineString" -> {
                for (i in 0 until coords.length()) {
                    val c = coords.optJSONArray(i) ?: continue
                    if (c.length() >= 2) {
                        out.add(GeoPoint(c.getDouble(1), c.getDouble(0), if (c.length() > 2) c.optDouble(2) else null))
                    }
                }
            }
            "MultiLineString" -> {
                for (i in 0 until coords.length()) {
                    val line = coords.optJSONArray(i) ?: continue
                    for (j in 0 until line.length()) {
                        val c = line.optJSONArray(j) ?: continue
                        if (c.length() >= 2) {
                            out.add(GeoPoint(c.getDouble(1), c.getDouble(0), if (c.length() > 2) c.optDouble(2) else null))
                        }
                    }
                }
            }
        }
    }

    private fun buildResult(
        title: String,
        format: String,
        points: List<GeoPoint>,
        waypoints: List<Waypoint>
    ): ParsedRouteData {
        val distMeters = HaversineDistanceCalculator.totalDistanceMeters(points)
        val metrics = ElevationMetricsCalculator.calculate(points)
        val curves = countSignificantCurves(points)
        return ParsedRouteData(
            title = title,
            format = format,
            points = points,
            waypoints = waypoints,
            distanceKm = distMeters / 1000.0,
            elevationGainMeters = metrics.gainMeters,
            curveCount = curves
        )
    }

    fun countSignificantCurves(points: List<GeoPoint>): Int {
        if (points.size < 3) return 0
        var count = 0
        var lastBearing: Double? = null
        var anchor = points.first()

        for (i in 1 until points.size) {
            val current = points[i]
            val segDist = HaversineDistanceCalculator.distanceMeters(anchor, current)
            if (segDist < 35.0 && i < points.lastIndex) continue

            val bearing = bearingDegrees(anchor, current)
            if (lastBearing != null) {
                var diff = bearing - lastBearing
                while (diff > 180) diff -= 360
                while (diff < -180) diff += 360
                if (abs(diff) >= 24.0) {
                    count++
                }
            }
            lastBearing = bearing
            anchor = current
        }
        return count
    }

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
