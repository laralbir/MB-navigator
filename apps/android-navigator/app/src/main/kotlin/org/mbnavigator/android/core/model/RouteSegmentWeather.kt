package org.mbnavigator.android.core.model

import java.time.Instant

enum class MeteoAlertLevel {
    NONE,
    YELLOW,  // Aviso informativo
    ORANGE,  // Riesgo importante (alerta prioritaria)
    RED      // Riesgo extraordinario (sugerir ruta alternativa)
}

enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    RAIN_LIGHT,
    RAIN_HEAVY,
    THUNDERSTORM,
    SNOW,
    ICE_HAZARD,
    WIND_GALE
}

/**
 * Meteorología calculada para un tramo según la hora estimada de paso.
 */
data class RouteSegmentWeather(
    val segmentIndex: Int,
    val coordinate: GeoPoint,
    val estimatedArrivalTime: Instant,
    val temperatureCelsius: Double,
    val precipitationProbabilityPercent: Int,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val windGustKmh: Double = 0.0,
    val condition: WeatherCondition,
    val alertLevel: MeteoAlertLevel = MeteoAlertLevel.NONE,
    val alertDescription: String? = null
)
