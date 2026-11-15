package org.mbnavigator.android.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint

class NavigationFeaturesTest {

    @Test
    fun `speed auto zoom scales smoothly from low urban speeds to highway cruise`() {
        val cityZoom = NavigationDisplayCalculator.computeTargetZoomForSpeed(15f)
        val mountainZoom = NavigationDisplayCalculator.computeTargetZoomForSpeed(60f)
        val highwayZoom = NavigationDisplayCalculator.computeTargetZoomForSpeed(120f)
        val fastCruiseZoom = NavigationDisplayCalculator.computeTargetZoomForSpeed(140f)

        assertEquals(17.2, cityZoom, 0.01)
        assertTrue(cityZoom > mountainZoom)
        assertTrue(mountainZoom > highwayZoom)
        assertEquals(13.4, fastCruiseZoom, 0.01)
    }

    @Test
    fun `zoom hysteresis suppresses micro changes but triggers meaningful changes`() {
        assertFalse(NavigationDisplayCalculator.shouldUpdateAutoZoom(15.5, 15.7))
        assertTrue(NavigationDisplayCalculator.shouldUpdateAutoZoom(15.5, 16.0))
        assertTrue(NavigationDisplayCalculator.shouldUpdateAutoZoom(16.0, 14.5))
    }

    @Test
    fun `valhalla polyline6 decoder decodes coordinates cleanly`() {
        // Dos puntos cercanos encodeados en precision 1e-6: (40.4168, -3.7038) -> (40.4200, -3.7000)
        // Verificamos estabilidad con polyline vacío y cadena válida conocida
        assertTrue(OpenRoutingClient.decodePolyline6("").isEmpty())
        val pts = OpenRoutingClient.decodePolyline6("_izlhA~rlgdF_{geC~ywl@")
        assertTrue(pts.size == 2)
    }

    @Test
    fun `dgt datex xml parser extracts roadworks and congestion coordinates with severity`() {
        val sampleXml = """
            <d2:payload>
              <sit:situation id="s1">
                <sit:situationRecord id="r1" version="1">
                  <sit:causeType>accident</sit:causeType>
                  <sit:overallSeverity>high</sit:overallSeverity>
                  <loc:roadNumber>A-6</loc:roadNumber>
                  <loc:latitude>40.4812</loc:latitude>
                  <loc:longitude>-3.8541</loc:longitude>
                </sit:situationRecord>
              </sit:situation>
              <sit:situation id="s2">
                <sit:situationRecord id="r2" version="1">
                  <sit:causeType>roadworks</sit:causeType>
                  <sit:overallSeverity>medium</sit:overallSeverity>
                  <loc:roadNumber>N-320</loc:roadNumber>
                  <loc:latitude>40.5010</loc:latitude>
                  <loc:longitude>-2.7110</loc:longitude>
                </sit:situationRecord>
              </sit:situation>
            </d2:payload>
        """.trimIndent()

        val parsed = TrafficDensityClient.parseDgtDatexXmlStream(sampleXml)
        assertEquals(2, parsed.size)
        assertEquals(TrafficDensityLevel.CONGESTED, parsed[0].level)
        assertEquals("A-6", parsed[0].roadName)
        assertEquals(40.4812, parsed[0].point.latitude, 0.0001)
        assertEquals(TrafficDensityLevel.MODERATE, parsed[1].level)

        val segs = TrafficDensityClient.buildRouteTrafficSegments(
            routePoints = listOf(GeoPoint(40.4800, -3.8530), GeoPoint(40.4820, -3.8550), GeoPoint(40.6000, -3.9900)),
            incidents = parsed
        )
        assertEquals(2, segs.size)
        assertEquals(TrafficDensityLevel.CONGESTED, segs[0].level)
        assertEquals(TrafficDensityLevel.FREE_FLOW, segs[1].level)
    }

    @Test
    fun `navigation run state transitions follow start pause resume stop lifecycle`() {
        NavigationSessionRepository.clearActiveRoute()
        assertEquals(NavigationRunState.IDLE, NavigationSessionRepository.navigationState.value)

        val route = SavedRouteEntry(
            id = "test-1",
            title = "Puerto Test",
            distanceKm = 25.0,
            elevationGainM = 500.0,
            curveCount = 20,
            estimatedCurviness = "Alta",
            format = "GPX",
            dateAdded = "Hoy",
            points = listOf(GeoPoint(40.7, -4.0), GeoPoint(40.8, -3.9))
        )

        NavigationSessionRepository.startNavigatingRoute(route, previewSimulation = false, autoStart = false)
        assertEquals(NavigationRunState.READY, NavigationSessionRepository.navigationState.value)

        NavigationSessionRepository.startNavigation()
        assertEquals(NavigationRunState.NAVIGATING, NavigationSessionRepository.navigationState.value)

        NavigationSessionRepository.pauseNavigation()
        assertEquals(NavigationRunState.PAUSED, NavigationSessionRepository.navigationState.value)

        NavigationSessionRepository.resumeNavigation()
        assertEquals(NavigationRunState.NAVIGATING, NavigationSessionRepository.navigationState.value)

        NavigationSessionRepository.stopNavigation()
        assertEquals(NavigationRunState.READY, NavigationSessionRepository.navigationState.value)

        NavigationSessionRepository.clearActiveRoute()
        assertEquals(NavigationRunState.IDLE, NavigationSessionRepository.navigationState.value)
    }
}
