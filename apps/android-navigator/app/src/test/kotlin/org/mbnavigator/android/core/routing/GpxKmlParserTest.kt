package org.mbnavigator.android.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint

class GpxKmlParserTest {

    private fun loadFixture(name: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Fixture no encontrada: $name"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun test_parses_real_morella_gpx_1_1_with_viapoints_shapingpoints_and_maneuvers() {
        val gpx = loadFixture("Morella_gpx1.1.gpx")
        val parsed = GpxKmlParser.parse(gpx, "Fallback")

        assertEquals("Morella", parsed.title)
        assertEquals("GPX 1.1", parsed.format)
        assertTrue("Debe parsear puntos detallados de track (> 100)", parsed.points.size > 100)
        assertTrue("La distancia debe superar 50 km (fue ${parsed.distanceKm})", parsed.distanceKm > 50.0)
        assertTrue("Debe extraer waypoints/viapoints de ruta", parsed.waypoints.isNotEmpty())
        assertTrue(
            "Debe distinguir ViaPoints obligatorios (Palacio de congresos)",
            parsed.waypoints.any { it.name.contains("Palacio", ignoreCase = true) && it.isMandatoryPassThrough }
        )
        assertTrue(
            "Debe distinguir ShapingPoints (isMandatoryPassThrough = false)",
            parsed.waypoints.any { !it.isMandatoryPassThrough }
        )

        val maneuvers = TurnByTurnEngine.extractManeuversFromTrack(parsed.points, parsed.title)
        assertTrue("Debe extraer maniobras Turn-by-Turn del trazado GPX", maneuvers.size > 5)
        assertEquals(ManeuverType.DEPART, maneuvers.first().type)
        assertEquals(ManeuverType.ARRIVE, maneuvers.last().type)
    }

    @Test
    fun test_parses_real_morella_gpx_1_2_with_gpxx_routepoint_extensions() {
        val gpx = loadFixture("Morella_gpx1.2.gpx")
        val parsed = GpxKmlParser.parse(gpx, "Fallback")

        assertEquals("Morella", parsed.title)
        assertEquals("GPX 1.2", parsed.format)
        assertTrue("Debe parsear puntos detallados de track/extensión", parsed.points.size > 100)
        assertTrue("Debe conservar la distancia real de ruta", parsed.distanceKm > 50.0)
        assertTrue("Debe extraer waypoints", parsed.waypoints.isNotEmpty())
    }

    @Test
    fun test_gpx_1_2_without_trk_uses_nested_gpxx_rpt_in_order() {
        val pureRte12 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:trp="http://www.garmin.com/xmlschemas/TripExtensions/v1"
                 xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3">
              <rte>
                <name>Ruta 1.2 Pura</name>
                <rtept lat="40.0000" lon="-0.1000">
                  <name>Inicio</name>
                  <extensions>
                    <trp:ViaPoint/>
                    <gpxx:RoutePointExtension>
                      <gpxx:rpt lat="40.0050" lon="-0.1020"/>
                      <gpxx:rpt lat="40.0100" lon="-0.1040"/>
                    </gpxx:RoutePointExtension>
                  </extensions>
                </rtept>
                <rtept lat="40.0200" lon="-0.1100">
                  <name>Final</name>
                  <extensions><trp:ViaPoint/></extensions>
                </rtept>
              </rte>
            </gpx>
        """.trimIndent()

        val parsed = GpxKmlParser.parse(pureRte12, "Fallback")
        assertEquals("GPX 1.2", parsed.format)
        assertEquals(4, parsed.points.size)
        assertEquals(40.0000, parsed.points[0].latitude, 1e-5)
        assertEquals(40.0050, parsed.points[1].latitude, 1e-5)
        assertEquals(40.0100, parsed.points[2].latitude, 1e-5)
        assertEquals(40.0200, parsed.points[3].latitude, 1e-5)
        assertEquals(2, parsed.waypoints.size)
    }

    @Test
    fun test_turn_by_turn_progress_and_voice_thresholds() {
        val track = listOf(
            GeoPoint(40.0000, -3.7000),
            GeoPoint(40.0040, -3.7000),
            GeoPoint(40.0080, -3.6940), // giro notable al noreste/este
            GeoPoint(40.0080, -3.6850)
        )
        val maneuvers = TurnByTurnEngine.extractManeuversFromTrack(track, "Prueba")
        val progress = TurnByTurnEngine.evaluateProgress(
            currentPosition = GeoPoint(40.0010, -3.7000),
            points = track,
            maneuvers = maneuvers,
            currentSpeedKmh = 60f
        )
        assertNotNull(progress)
        assertFalse(progress!!.isOffRoute)
        assertTrue(progress.remainingDistanceMeters > 500.0)

        val t1 = TurnByTurnEngine.determineVoicePromptThreshold(1, 500.0, null)
        assertEquals(520, t1)
        val repeat = TurnByTurnEngine.determineVoicePromptThreshold(1, 480.0, 1 to 520)
        assertEquals(null, repeat)
        val closer = TurnByTurnEngine.determineVoicePromptThreshold(1, 150.0, 1 to 520)
        assertEquals(160, closer)
    }
}
