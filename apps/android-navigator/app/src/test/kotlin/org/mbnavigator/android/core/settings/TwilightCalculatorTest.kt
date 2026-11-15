package org.mbnavigator.android.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbnavigator.android.core.model.GeoPoint
import java.time.ZoneId
import java.time.ZonedDateTime

class TwilightCalculatorTest {

    @Test
    fun `sunrise and sunset for madrid equinox are sensible`() {
        val pair = TwilightCalculator.computeSunriseSunsetUtcMinutes(
            dayOfYear = 80, // finales de marzo
            latitude = 40.4168,
            longitude = -3.7038
        )
        assertNotNull(pair)
        val (sunriseMin, sunsetMin) = pair!!
        // En Madrid en marzo, amanecer ronda ~06:15 UTC (375 min) y ocaso ~18:30 UTC (1110 min)
        assertTrue("Amanecer esperado entre 330 y 450 UTC min, real=$sunriseMin", sunriseMin in 330.0..450.0)
        assertTrue("Ocaso esperado entre 1050 y 1170 UTC min, real=$sunsetMin", sunsetMin in 1050.0..1170.0)
    }

    @Test
    fun `daytime noon is light and midnight is night for spain gps`() {
        val madrid = GeoPoint(40.4168, -3.7038)
        val zone = ZoneId.of("Europe/Madrid")

        val noon = ZonedDateTime.of(2026, 6, 21, 13, 30, 0, 0, zone)
        assertFalse(TwilightCalculator.isNightTime(madrid, noon))

        val midnight = ZonedDateTime.of(2026, 6, 21, 23, 55, 0, 0, zone)
        assertTrue(TwilightCalculator.isNightTime(madrid, midnight))
    }

    @Test
    fun `null location falls back cleanly to standard dusk-dawn window`() {
        val zone = ZoneId.of("UTC")
        assertFalse(TwilightCalculator.isNightTime(null, ZonedDateTime.of(2026, 11, 15, 14, 0, 0, 0, zone)))
        assertTrue(TwilightCalculator.isNightTime(null, ZonedDateTime.of(2026, 11, 15, 22, 0, 0, 0, zone)))
        assertTrue(TwilightCalculator.isNightTime(null, ZonedDateTime.of(2026, 11, 15, 6, 30, 0, 0, zone)))
    }

    @Test
    fun `favorites serialization round-trip parses categories safely`() {
        val sample = listOf(
            FavoriteDestination("fav_home", "Casa", FavoritePlaceCategory.HOME, "Calle Mayor 1", 40.41, -3.70),
            FavoriteDestination("fav_work", "Trabajo", FavoritePlaceCategory.WORK, "Paseo Castellana 10", 40.44, -3.69)
        )
        val encoded = AppPreferencesManager.serializeFavorites(sample)
        val parsed = AppPreferencesManager.parseFavoritesJson(encoded)
        assertEquals(2, parsed.size)
        assertEquals(FavoritePlaceCategory.HOME, parsed[0].category)
        assertEquals("Casa", parsed[0].name)
        assertEquals(FavoritePlaceCategory.WORK, parsed[1].category)
    }
}
