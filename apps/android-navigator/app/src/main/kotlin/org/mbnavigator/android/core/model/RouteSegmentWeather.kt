package org.mbnavigator.android.core.model

import java.time.Instant

enum class MeteoAlertLevel {
    NONE,
    YELLOW,  // Aviso informativo
    ORANGE,  // Riesgo importante (alerta prioritaria en moto)
    RED      // Riesgo extraordinario (peligro extremo en moto, sugerir ruta alternativa)
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
 * Meteorología calculada para un tramo según la hora estimada de paso,
 * con análisis de riesgos específicos para conducción en motocicleta.
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
) {
    /**
     * Peligro de viento lateral cruzado para motocicletas (ráfagas > 45 km/h o viento constante > 35 km/h).
     */
    val isCrosswindHazardForMotorcycle: Boolean
        get() = windGustKmh >= 45.0 || windSpeedKmh >= 35.0

    /**
     * Peligro de asfalto mojado / pérdida de adherencia en curva.
     */
    val isWetRoadRisk: Boolean
        get() = precipitationMm > 0.5 || condition in listOf(
            WeatherCondition.RAIN_LIGHT,
            WeatherCondition.RAIN_HEAVY,
            WeatherCondition.THUNDERSTORM
        )

    /**
     * Riesgo de placas de hielo negro (*black ice*) en curvas sombrías o puertos de montaña.
     */
    val isFreezingAsphaltRisk: Boolean
        get() = temperatureCelsius <= 3.0 && (isWetRoadRisk || condition == WeatherCondition.ICE_HAZARD)
}
