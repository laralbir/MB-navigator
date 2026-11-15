# SPEC-002: Especificación Funcional y Técnica de Android Navigator

- **Estado**: Aprobado
- **App de destino**: `apps/android-navigator`
- **Fecha**: 2026-11-15
- **Autores**: MB-navigator Core Team

---

## 1. Resumen Ejecutivo
`android-navigator` es la aplicación móvil de navegación GPS nativa (Kotlin + Jetpack Compose) del ecosistema MB-navigator. Proporciona navegación paso a paso con mapas offline/online libres, alertas de tráfico en tiempo real (integración con DGT 3.0), detección de radares e incidencias, previsión meteorológica predictiva por tramo según hora estimada de paso (Open-Meteo y AEMET Meteoalerta), compatibilidad con Android Auto, asistente opcional con IA (Google Gemini) y registro estadístico detallado de viajes.

---

## 2. Requisitos Funcionales Detallados

### 2.1. Mapas Libres Offline y Online (Sin Licencias Comerciales)
- **RF-MAP-01 (Mapas Offline)**:
  - Soporte de paquetes vectoriales en formato abierto **PMTiles** y **MBTiles** generados a partir de OpenStreetMap.
  - Gestor de descargas integrado por divisiones administrativas (país, comunidad autónoma, provincia).
  - Renderizado autónomo por GPU con MapLibre Native sin necesidad de conexión de red ni registro previo.
- **RF-MAP-02 (Capas Online Abiertas)**:
  - Acceso directo a servidores de teselas públicas: OpenStreetMap Estándar, OpenTopoMap, CyclOSM y capas de sombreado de relieve (hillshading).
  - Caché local en almacenamiento interno para navegación fluida y ahorro de datos móviles.

---

### 2.2. Guía por Voz y Comandos con Niveles de Verbosidad
- **RF-VOICE-01 (Motor de Voz)**:
  - Síntesis de voz offline mediante `android.speech.tts.TextToSpeech`.
- **RF-VOICE-02 (Niveles de Verbosidad Configurables)**:
  - **Mínimo**: Solo alertas de peligro crítico, desvíos obligatorios y giros inmediatos (< 100 m).
  - **Estándar**: Anuncio previo a 500 m / 1 km, confirmación a 100 m con nombre de vía y maniobra.
  - **Detallado**: Carril recomendado, referencias de distancia anticipada, avisos meteorológicos y descripción del entorno.
- **RF-VOICE-03 (Comandos por Voz)**:
  - Activación por botón en pantalla o control del volante en Android Auto.
  - Comandos disponibles: *"¿Cómo está el tiempo más adelante?"*, *"Cancelar ruta"*, *"Buscar gasolinera"*, *"Silenciar voz"*, *"¿Cuánto queda?"*.

---

### 2.3. Tráfico, Cortes de Carretera y Recálculo Dinámico
- **RF-TRAFFIC-01 (Detección de Incidentes)**:
  - Recepción de cortes viales, retenciones, obras y desvíos programados.
- **RF-TRAFFIC-02 (Recálculo Automático)**:
  - Cuando se detecta un incidente en la ruta activa que añada un retraso superior a un umbral configurable (ej. > 5 min), el motor propone rutas alternativas automáticas con comparativa de tiempo y distancia.
- **RF-TRAFFIC-03 (Recálculo por Pérdida de Trazado)**:
  - Si el usuario se desvía más de 30 metros del trazado, BRouter calcula de inmediato el reenganche más eficiente.

---

### 2.4. Radares, Obstáculos y Conexión con DGT 3.0
- **RF-SAFETY-01 (Aviso de Radares)**:
  - Base de datos local offline de radares fijos, de tramo y zonas habituales de móviles basada en OpenStreetMap (`highway=speed_camera`) y datasets públicos abiertos.
  - Aviso visual y acústico progresivo según la velocidad actual del vehículo y la proximidad al radar.
- **RF-SAFETY-02 (Conexión DGT 3.0 / Punto de Acceso Nacional DATEX II)**:
  - Consumo del feed abierto de incidencias en tiempo real de la Dirección General de Tráfico (Punto de Acceso Nacional de Tráfico en formato DATEX II / JSON).
  - Alertas geolocalizadas de:
    - Balizas V16 conectadas activas (vehículos detenidos en el arcén o calzada).
    - Conos de obras inteligentes y cortes de emergencia.
    - Presencia de hielo, niebla densa o nieve en calzada.

---

### 2.5. Compatibilidad con Android Auto
- **RF-CAR-01 (Android for Cars App Library)**:
  - Implementación con `androidx.car.app:app` y `androidx.car.app:app-automotive`.
  - Plantillas de navegación nativas (`NavigationTemplate`) compatibles con la consola central de vehículos.
  - Controles optimizados para mandos físicos del coche, ruletas giratorias y pantallas táctiles vehiculares sin comprometer la seguridad al volante.

---

### 2.6. Importación y Ruteo por Puntos de Interés (POIs Obligatorios)
- **RF-ROUTE-01 (Formatos de Entrada)**:
  - Importación y exportación de archivos **GPX 1.1**, **KML** y **GeoJSON**.
- **RF-ROUTE-02 (Puntos de Paso Obligatorios - Waypoints)**:
  - El usuario puede definir uno o múltiples puntos de paso (*via points*) intermedios.
  - BRouter genera la ruta forzando el orden exacto de los waypoints seleccionados.
- **RF-ROUTE-03 (Límites de Velocidad)**:
  - Detección de la velocidad máxima permitida (`maxspeed`) por tramo desde OpenStreetMap.
  - Alerta visual destacada en caso de exceso de velocidad con umbral configurable (+5 km/h, +10 km/h).

---

### 2.7. Meteorología Predictiva por Tramo y Hora Estimada de Paso
- **RF-METEO-01 (Cálculo Temporal por Tramo)**:
  - Para cada tramo $k$ de la ruta, el sistema calcula la hora estimada de paso ($T_k$):
    $$T_k = T_{salida} + \sum_{i=0}^{k-1} \Delta t_i$$
- **RF-METEO-02 (Integración Open-Meteo)**:
  - Consulta de pronóstico horario por coordenadas geográficas (precipitación, nieve, rachas de viento, visibilidad y temperatura) para la hora exacta $T_k$.
- **RF-METEO-03 (Integración AEMET Meteoalerta)**:
  - Consulta de avisos meteorológicos oficiales en España (Plan Meteoalerta):
    - **Nivel Amarillo**: Riesgo para actividades al aire libre (aviso visual informativo).
    - **Nivel Naranja**: Riesgo importante (alerta prioritaria y recomendación de precaución).
    - **Nivel Rojo**: Riesgo extraordinario (alerta crítica con sugerencia automática de ruta alternativa para evitar la zona afectada).

---

### 2.8. Módulo de Inteligencia Artificial (Google Gemini) - Híbrido y Opcional
- **RF-AI-01 (Configuración de IA)**:
  - **Modo Desactivado (100% Offline / Privacidad Absoluta)**: No se establece conexión con ningún LLM; el asistente utiliza reglas deterministas internas y síntesis TTS estándar.
  - **Modo Gemini API (Configurable con clave personal del usuario)**:
    - Resumen conversacional previo al viaje: *"La ruta está despejada los primeros 60 km, pero entre las 16:30 y 17:15 cruzarás el puerto con aviso naranja por nieve y viento fuerte"*.
    - Asistente de consultas durante la ruta: *"¿Dónde puedo parar a comer antes de llegar a la zona de tormenta?"*.
  - **Modo Gemini Nano (En dispositivo si el hardware lo soporta)**: Inferencia local sin consumo de cuota externa.

---

### 2.9. Registro Estadístico y Telemetría Histórica
- **RF-STATS-01 (Métricas Almacenadas en Room DB)**:
  - Distancia total recorrida y tiempo en movimiento vs. tiempo detenido.
  - Velocidad media, velocidad máxima y gráfica de velocidad a lo largo del tiempo.
  - Condiciones climáticas registradas durante la actividad (temperatura mínima/máxima, presencia de lluvia o viento).
  - Perfil de elevación real con desnivel positivo acumulado (+D) y negativo (-D).
- **RF-STATS-02 (Exportación)**:
  - Exportación de la actividad completa en formato GPX enriquecido con extensiones de velocidad, altitud y meteorología.

---

## 3. Contratos de Datos Clave

```kotlin
data class RouteSegmentWeather(
    val segmentIndex: Int,
    val coordinate: GeoPoint,
    val estimatedArrivalTime: Instant,
    val temperatureCelsius: Double,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val weatherCondition: WeatherType,
    val alertLevel: MeteoAlertLevel // NONE, YELLOW, ORANGE, RED
)

enum class MeteoAlertLevel { NONE, YELLOW, ORANGE, RED }

data class TrafficIncident(
    val id: String,
    val source: TrafficSource, // DGT_3_0, OPEN_DATA, OSM
    val location: GeoPoint,
    val type: IncidentType, // ACCIDENT, ROAD_CLOSURE, ROADWORK, V16_EMERGENCY, OBSTACLE
    val description: String,
    val delayMinutes: Int?,
    val timestamp: Instant
)

data class TripTelemetryRecord(
    val tripId: String,
    val startTime: Instant,
    val endTime: Instant,
    val totalDistanceMeters: Double,
    val movingTimeSeconds: Long,
    val stoppedTimeSeconds: Long,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minTempCelsius: Double?,
    val maxTempCelsius: Double?,
    val adverseWeatherEncountered: Boolean
)
```

---

## 4. Casos de Prueba TDD (Suite de Aceptación)

- [ ] `test_route_calculation_strictly_visits_all_mandatory_waypoints_in_order`
- [ ] `test_offline_routing_operates_without_internet_using_local_brouter_files`
- [ ] `test_estimated_time_of_arrival_correctly_interpolates_weather_at_each_segment`
- [ ] `test_red_meteo_alert_triggers_alternative_route_prompt`
- [ ] `test_dgt30_v16_incident_shows_warning_when_within_proximity_radius`
- [ ] `test_speed_exceeding_maxspeed_triggers_warning_according_to_configured_threshold`
- [ ] `test_ai_module_falls_back_gracefully_when_gemini_api_key_is_absent`
- [ ] `test_trip_telemetry_persists_correctly_to_room_and_exports_to_valid_gpx`
