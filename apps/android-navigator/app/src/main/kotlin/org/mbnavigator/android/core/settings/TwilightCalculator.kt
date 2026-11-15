package org.mbnavigator.android.core.settings

import org.mbnavigator.android.core.model.GeoPoint
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculador astronómico offline de horas crepusculares (orto y ocaso solar)
 * a partir de las coordenadas GPS y la fecha local del dispositivo.
 */
object TwilightCalculator {

    /**
     * Determina si es de noche / crepúsculo civil (elevación solar < -0.833°) en la ubicación dada.
     * Si no hay coordenada GPS disponible aún, usa una ventana horaria segura (20:30 - 07:15).
     */
    fun isNightTime(
        location: GeoPoint?,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ): Boolean {
        val lat = location?.latitude
        val lon = location?.longitude
        if (lat == null || lon == null) {
            val minutes = now.hour * 60 + now.minute
            return minutes >= (20 * 60 + 30) || minutes < (7 * 60 + 15)
        }

        val (sunriseMinutesUtc, sunsetMinutesUtc) = computeSunriseSunsetUtcMinutes(
            dayOfYear = now.dayOfYear,
            latitude = lat,
            longitude = lon
        ) ?: return false // Día polar / sol de medianoche

        val offsetMinutes = now.offset.totalSeconds / 60.0
        val currentMinutesUtc = ((now.hour * 60.0 + now.minute) - offsetMinutes).let { m ->
            ((m % 1440.0) + 1440.0) % 1440.0
        }

        val sr = ((sunriseMinutesUtc % 1440.0) + 1440.0) % 1440.0
        val ss = ((sunsetMinutesUtc % 1440.0) + 1440.0) % 1440.0

        return if (sr < ss) {
            currentMinutesUtc < sr || currentMinutesUtc >= ss
        } else {
            // Cruce de medianoche UTC (p. ej. husos americanos u oceánicos)
            currentMinutesUtc < sr && currentMinutesUtc >= ss
        }
    }

    /**
     * Calcula los minutos desde medianoche UTC del orto y ocaso solar (algoritmo NOAA simplificado).
     */
    fun computeSunriseSunsetUtcMinutes(
        dayOfYear: Int,
        latitude: Double,
        longitude: Double
    ): Pair<Double, Double>? {
        val clampedLat = latitude.coerceIn(-89.8, 89.8)
        val gamma = (2.0 * Math.PI / 365.0) * (dayOfYear - 1)

        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(clampedLat)
        val zenithRad = Math.toRadians(90.833)
        val cosHa = (cos(zenithRad) / (cos(latRad) * cos(decl))) - (tan(latRad) * tan(decl))
        if (cosHa > 1.0 || cosHa < -1.0) return null

        val haDeg = Math.toDegrees(acos(cosHa))
        val sunriseUtc = 720.0 - 4.0 * (longitude + haDeg) - eqTime
        val sunsetUtc = 720.0 - 4.0 * (longitude - haDeg) - eqTime
        return floor(sunriseUtc) to floor(sunsetUtc)
    }
}
