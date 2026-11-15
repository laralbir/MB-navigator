package org.mbnavigator.android.core.routing

import androidx.car.app.navigation.model.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.auto.CarIconFactory
import org.mbnavigator.android.core.location.LocationStabilizer
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.traffic.SafetyHazardAlertEngine

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
    fun `shortest angle delta wraps smoothly across north boundary`() {
        assertEquals(2f, NavigationDisplayCalculator.shortestAngleDeltaDegrees(359f, 1f), 0.01f)
        assertEquals(-2f, NavigationDisplayCalculator.shortestAngleDeltaDegrees(1f, 359f), 0.01f)
        assertEquals(-90f, NavigationDisplayCalculator.shortestAngleDeltaDegrees(180f, 90f), 0.01f)
        assertFalse(NavigationDisplayCalculator.shouldUpdateHeadingBearing(2f, isSimulation = false))
        assertTrue(NavigationDisplayCalculator.shouldUpdateHeadingBearing(15f, isSimulation = false))
        assertTrue(NavigationDisplayCalculator.shouldUpdateHeadingBearing(0f, isSimulation = true))
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

    @Test
    fun `voice guidance toggle persists false state without resetting`() {
        AppPreferencesManager.setVoiceGuidanceEnabled(false)
        assertFalse(AppPreferencesManager.voiceGuidanceEnabled.value)

        // Cambiar preferencias de seguridad o tráfico no reactiva la voz de giros
        AppPreferencesManager.setDgtAlertsEnabled(true)
        AppPreferencesManager.setSpeedCameraAlertsEnabled(true)
        assertFalse(AppPreferencesManager.voiceGuidanceEnabled.value)

        AppPreferencesManager.setVoiceGuidanceEnabled(true)
        assertTrue(AppPreferencesManager.voiceGuidanceEnabled.value)
    }

    @Test
    fun `safety alerts for radars, broken down vehicles and accidents trigger even with muted voice`() {
        AppPreferencesManager.setVoiceGuidanceEnabled(false)

        val brokenDownXml = """
            <d2:payload>
              <sit:situation id="v16">
                <sit:situationRecord id="veh1" version="1">
                  <sit:causeType>vehicleObstruction</sit:causeType>
                  <sit:vehicleObstructionType>vehicleStuck</sit:vehicleObstructionType>
                  <sit:overallSeverity>medium</sit:overallSeverity>
                  <loc:roadName>N-340</loc:roadName>
                  <loc:latitude>40.4020</loc:latitude>
                  <loc:longitude>-3.7000</loc:longitude>
                </sit:situationRecord>
              </sit:situation>
            </d2:payload>
        """.trimIndent()
        val incidents = TrafficDensityClient.parseDgtDatexXmlStream(brokenDownXml)
        assertEquals(1, incidents.size)
        assertEquals(HazardCategory.BROKEN_DOWN_VEHICLE, incidents.first().category)

        val radar = TrafficIncidentPoint(
            id = "cam-100",
            point = GeoPoint(40.4015, -3.7000),
            level = TrafficDensityLevel.BLOCKED,
            roadName = "A-1",
            description = "A-1 · Radar 120 km/h",
            category = HazardCategory.SPEED_CAMERA,
            speedLimitKmh = 120
        )

        val currentPos = GeoPoint(40.4000, -3.7000)
        val alert = SafetyHazardAlertEngine.evaluateActiveHazard(
            currentPosition = currentPos,
            routePoints = listOf(currentPos, GeoPoint(40.4050, -3.7000)),
            incidents = incidents + radar,
            dgtAlertsEnabled = true,
            speedCameraAlertsEnabled = true
        )
        assertNotNull(alert)
        assertEquals(HazardCategory.BROKEN_DOWN_VEHICLE, alert!!.incident.category)
        assertTrue(
            SafetyHazardAlertEngine.shouldAnnounceSafetyAlert(
                alert = alert,
                dgtAlertsEnabled = true,
                speedCameraAlertsEnabled = true,
                announcedKeys = emptySet()
            )
        )
        assertTrue(SafetyHazardAlertEngine.formatHazardSpeech(alert).contains("vehículo averiado"))

        val suppressedDgtAlert = SafetyHazardAlertEngine.evaluateActiveHazard(
            currentPosition = currentPos,
            routePoints = null,
            incidents = incidents,
            dgtAlertsEnabled = false,
            speedCameraAlertsEnabled = true
        )
        assertNull(suppressedDgtAlert)

        val radarAlert = SafetyHazardAlertEngine.evaluateActiveHazard(
            currentPosition = currentPos,
            routePoints = null,
            incidents = listOf(radar),
            dgtAlertsEnabled = false,
            speedCameraAlertsEnabled = true
        )
        assertNotNull(radarAlert)
        assertTrue(SafetyHazardAlertEngine.formatHazardSpeech(radarAlert!!).contains("120"))

        AppPreferencesManager.setVoiceGuidanceEnabled(true)
    }

    @Test
    fun `traffic segments preserve intermediate curve vertices and road snapping detects sparse tracks`() {
        // 300 puntos muy juntos (~20 m) no deben necesitar snap; 3 puntos distantes (> 1 km) sí
        val sparse = listOf(GeoPoint(40.72, -4.00), GeoPoint(40.79, -4.00), GeoPoint(40.82, -3.96))
        assertTrue(OpenRoutingClient.needsRoadSnapping(sparse))

        val dense = (0..100).map { i -> GeoPoint(40.4000 + i * 0.0002, -3.7000) }
        assertFalse(OpenRoutingClient.needsRoadSnapping(dense))

        // Con 250 puntos y stride = 2, cada TrafficSegmentColor ha de conservar vértices intermedios (>2)
        val curvedDense = (0..250).map { i -> GeoPoint(40.4000 + i * 0.0002, -3.7000 + (i % 5) * 0.0001) }
        val segs = TrafficDensityClient.buildRouteTrafficSegments(curvedDense, emptyList())
        assertTrue(segs.first().points.size >= 3)
    }

    @Test
    fun `location stabilizer freezes cursor when stationary and ignores coarse network jumps`() {
        val t0 = 1_700_000_000_000L
        val anchor = GeoPoint(
            latitude = 40.41680,
            longitude = -3.70380,
            speedMps = 0.2f,
            bearingDegrees = 90f,
            accuracyMeters = 5f
        )

        val first = LocationStabilizer.stabilize(
            previousAnchored = null,
            lastAccurateFixMs = 0L,
            candidate = anchor,
            provider = "gps",
            nowMs = t0
        )
        assertTrue(first.moved)
        assertEquals(0f, first.location.speedMps ?: -1f, 0.01f)

        // Deriva estática de ~3 m y velocidad residual 0.5 m/s -> el cursor permanece anclado sin moverse
        val jitter = anchor.copy(latitude = 40.416825, longitude = -3.703815, speedMps = 0.5f, bearingDegrees = 210f)
        val frozen = LocationStabilizer.stabilize(
            previousAnchored = first.location,
            lastAccurateFixMs = first.lastAccurateFixMs,
            candidate = jitter,
            provider = "gps",
            nowMs = t0 + 1000L
        )
        assertFalse(frozen.moved)
        assertEquals(40.41680, frozen.location.latitude, 0.000001)
        assertEquals(-3.70380, frozen.location.longitude, 0.000001)
        assertEquals(90f, frozen.location.bearingDegrees ?: -1f, 0.01f)

        // Salto impreciso de antena de red (~120 m) mientras hay fix GPS reciente -> ignorado
        val networkJump = GeoPoint(40.41790, -3.70250, speedMps = 0f, accuracyMeters = 80f)
        val ignoredNet = LocationStabilizer.stabilize(
            previousAnchored = frozen.location,
            lastAccurateFixMs = frozen.lastAccurateFixMs,
            candidate = networkJump,
            provider = "network",
            nowMs = t0 + 3000L
        )
        assertFalse(ignoredNet.moved)
        assertEquals(40.41680, ignoredNet.location.latitude, 0.000001)

        // Arranca en moto (8 m/s ~ 29 km/h, precisión 4 m) -> el cursor avanza normalmente
        val moving = GeoPoint(40.41710, -3.70350, speedMps = 8.0f, bearingDegrees = 45f, accuracyMeters = 4f)
        val progressed = LocationStabilizer.stabilize(
            previousAnchored = ignoredNet.location,
            lastAccurateFixMs = ignoredNet.lastAccurateFixMs,
            candidate = moving,
            provider = "fused",
            nowMs = t0 + 5000L
        )
        assertTrue(progressed.moved)
        assertEquals(40.41710, progressed.location.latitude, 0.000001)
        assertEquals(45f, progressed.location.bearingDegrees ?: -1f, 0.01f)
    }

    @Test
    fun `car icon factory maps every maneuver type to valid Android Auto maneuver constant`() {
        assertEquals(Maneuver.TYPE_DEPART, CarIconFactory.toCarManeuverType(ManeuverType.DEPART))
        assertEquals(Maneuver.TYPE_DESTINATION, CarIconFactory.toCarManeuverType(ManeuverType.ARRIVE))
        assertEquals(Maneuver.TYPE_TURN_NORMAL_LEFT, CarIconFactory.toCarManeuverType(ManeuverType.LEFT))
        assertEquals(Maneuver.TYPE_TURN_SLIGHT_LEFT, CarIconFactory.toCarManeuverType(ManeuverType.SLIGHT_LEFT))
        assertEquals(Maneuver.TYPE_TURN_SHARP_LEFT, CarIconFactory.toCarManeuverType(ManeuverType.SHARP_LEFT))
        assertEquals(Maneuver.TYPE_TURN_NORMAL_RIGHT, CarIconFactory.toCarManeuverType(ManeuverType.RIGHT))
        assertEquals(Maneuver.TYPE_TURN_SLIGHT_RIGHT, CarIconFactory.toCarManeuverType(ManeuverType.SLIGHT_RIGHT))
        assertEquals(Maneuver.TYPE_TURN_SHARP_RIGHT, CarIconFactory.toCarManeuverType(ManeuverType.SHARP_RIGHT))
        assertEquals(Maneuver.TYPE_U_TURN_LEFT, CarIconFactory.toCarManeuverType(ManeuverType.U_TURN))
        assertEquals(Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW, CarIconFactory.toCarManeuverType(ManeuverType.ROUNDABOUT))
        assertEquals(Maneuver.TYPE_STRAIGHT, CarIconFactory.toCarManeuverType(ManeuverType.STRAIGHT))
    }
}
