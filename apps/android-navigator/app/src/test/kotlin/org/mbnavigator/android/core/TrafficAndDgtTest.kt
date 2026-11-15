package org.mbnavigator.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.IncidentType
import org.mbnavigator.android.core.model.Track
import org.mbnavigator.android.core.model.TrackSegment
import org.mbnavigator.android.core.model.TrafficIncident
import org.mbnavigator.android.core.model.TrafficSource
import org.mbnavigator.android.core.traffic.MockTrafficAlertService

class TrafficAndDgtTest {

    @Test
    fun test_v16_beacon_near_route_is_reported() = runBlocking {
        val routePoint = GeoPoint(40.4000, -3.7000)
        val v16Incident = TrafficIncident(
            id = "V16-1234",
            source = TrafficSource.DGT_3_0,
            location = GeoPoint(40.4005, -3.7000), // a unos 55 metros de la ruta
            type = IncidentType.V16_EMERGENCY_BEACON,
            title = "Vehículo averiado",
            description = "Baliza V16 conectada activa en arcén derecho",
            isRoadBlocked = false
        )

        val farIncident = TrafficIncident(
            id = "FAR-9999",
            source = TrafficSource.DGT_3_0,
            location = GeoPoint(43.0000, -5.0000), // A cientos de kilómetros
            type = IncidentType.ROAD_CLOSURE,
            title = "Corte lejano",
            description = "Obras lejanas",
            isRoadBlocked = true
        )

        val trafficService = MockTrafficAlertService(
            injectedIncidents = listOf(v16Incident, farIncident)
        )

        val track = Track(
            name = "Ruta Urbana",
            segments = listOf(TrackSegment(listOf(routePoint)))
        )

        val incidentsNearRoute = trafficService.getIncidentsAlongRoute(track, proximityMeters = 500.0)

        assertEquals("Only nearby V16 beacon should be reported", 1, incidentsNearRoute.size)
        assertEquals("V16-1234", incidentsNearRoute.first().id)
        assertEquals(IncidentType.V16_EMERGENCY_BEACON, incidentsNearRoute.first().type)
    }
}
