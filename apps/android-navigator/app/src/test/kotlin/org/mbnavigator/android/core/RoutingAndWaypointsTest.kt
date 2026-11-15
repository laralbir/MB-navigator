package org.mbnavigator.android.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Waypoint
import org.mbnavigator.android.core.routing.MockRoutingEngine
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile

class RoutingAndWaypointsTest {

    @Test
    fun test_route_calculation_visits_all_mandatory_waypoints_in_order() = runBlocking {
        val router = MockRoutingEngine()

        val origin = GeoPoint(40.4168, -3.7038) // Madrid
        val dest = GeoPoint(41.3879, 2.16992)  // Barcelona
        val intermediatePoi1 = Waypoint(name = "Zaragoza", point = GeoPoint(41.6488, -0.8891))
        val intermediatePoi2 = Waypoint(name = "Lleida", point = GeoPoint(41.6176, 0.6200))

        val result = router.calculateRoute(
            origin = origin,
            destination = dest,
            viaPoints = listOf(intermediatePoi1, intermediatePoi2),
            profile = MotorcycleRoutingProfile.CURVY
        )

        assertTrue("Routing should succeed", result.isSuccess)
        val track = result.getOrThrow()

        val points = track.segments.first().points
        assertEquals("Track must have 4 points (origin, 2 waypoints, destination)", 4, points.size)
        assertEquals(origin, points[0])
        assertEquals(intermediatePoi1.point, points[1])
        assertEquals(intermediatePoi2.point, points[2])
        assertEquals(dest, points[3])
        assertTrue("Track name must reflect motorcycle profile", track.name.contains("Moto"))
    }

    @Test
    fun test_motorcycle_curvy_profile_adjusts_speed_and_duration() = runBlocking {
        val router = MockRoutingEngine()
        val origin = GeoPoint(40.0, -3.0)
        val dest = GeoPoint(40.5, -3.0) // ~55 km

        val curvyResult = router.calculateRoute(origin, dest, profile = MotorcycleRoutingProfile.CURVY).getOrThrow()
        val fastResult = router.calculateRoute(origin, dest, profile = MotorcycleRoutingProfile.FAST_HIGHWAY).getOrThrow()

        // En curvas la velocidad media estimada es menor y por tanto la duración estimada es mayor
        assertTrue(
            "Curvy duration should be longer than fast highway duration",
            curvyResult.statistics.durationSeconds > fastResult.statistics.durationSeconds
        )
    }
}
