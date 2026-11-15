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

### 2.9. Arquitectura Multi-Agente y Subagentes Autónomos
Para garantizar un rendimiento no bloqueante, alta resiliencia y procesamiento distribuido de eventos críticos en moto, la aplicación implementa una arquitectura completa basada en **Agentes y Subagentes Autónomos**:

#### 2.9.1. Agente Coordinador Maestro (Master Navigation Coordinator Agent)
- Orquesta el ciclo de vida de la sesión de navegación.
- Fusiona los flujos asíncronos (`Kotlin Coroutine Channels` y `StateFlow`) provenientes de todos los subagentes.
- Aplica políticas de arbitraje: las alertas de seguridad vial (DGT 3.0 / V16) y avisos meteorológicos críticos tienen prioridad estricta sobre la guía de voz convencional o consultas al copiloto IA.

#### 2.9.2. Subagentes Especializados de Ejecución Concurrente
1. **Subagente de Ruteo y Geometría Dinámica (`RoutingAgent`)**:
   - Monitorea continuamente la posición GPS y calcula la desviación perpendicular respecto al trazado activo.
   - Si la desviación supera los $30\text{ m}$, activa recálculo asíncrono con BRouter sin congelar la renderización ni la interfaz de usuario.
   - Aplica perfiles específicos para motocicletas (`CURVY`, `EXTRA_CURVY`, `FAST_HIGHWAY`, `ADVENTURE_TRAIL`).
2. **Subagente Centinela de Tráfico y Seguridad DGT 3.0 (`TrafficSentinelAgent`)**:
   - Realiza polling inteligente de incidencias del Punto de Acceso Nacional (DATEX II / DGT 3.0).
   - Ejecuta geofencing predictivo sobre los próximos 15 km de ruta para detectar balizas V16 activas, vehículos detenidos en curva ciega y obras de emergencia.
   - Emite alertas con distancias relativas exactas (ej. *"🚨 A 250 m: Peligro en calzada"*).
3. **Subagente de Meteorología Predictiva por Segmento (`PredictiveWeatherAgent`)**:
   - Conecta con Open-Meteo y AEMET Meteoalerta.
   - Interpola la hora estimada de llegada a cada segmento ($T_k$) y vigila peligros específicos de dos ruedas: rachas de viento lateral $> 45\text{ km/h}$, asfalto mojado en zonas de curvas y temperaturas $\le 3^\circ\text{C}$ propensas a placas de hielo negro.
4. **Subagente Copiloto Conversacional Gemini (`GeminiCopilotAgent`)**:
   - Procesa comandos de voz y consultas contextuales del motorista (manos libres a través de intercomunicador Bluetooth Cardo / Sena / Midland).
   - Genera resúmenes ejecutivos de etapa y busca alternativas gastronómicas o paradas de repostaje optimizadas por curvas.
5. **Subagente Gestor de Mapas y Almacenamiento Offline (`OfflineMapManagerAgent`)**:
   - Gestiona descargas concurrentes de paquetes vectoriales (PMTiles / MBTiles) y grafos BRouter de cualquier país o región del mundo.
   - Monitoriza el espacio libre en el almacenamiento interno (`StatFs`), calcula hashes SHA-256 de integridad y ofrece gestión completa de desinstalación para liberar espacio.
6. **Subagente de Síntesis y Modulación de Voz TTS (`VoiceGuidanceAgent`)**:
   - Administra la cola de prioridad auditiva, reduciendo temporalmente el volumen de música o intercomunicador para emitir instrucciones breves y claras con síntesis nativa offline.
7. **Subagente de Telemetría y Dinámica de Marcha (`TelemetryDynamicsAgent`)**:
   - Registra en Room DB las métricas cinemáticas del viaje: velocidad instantánea, medias, perfil altimétrico (+D / -D), tiempo en movimiento vs. detenido y condiciones ambientales encontradas para exportación GPX enriquecida.

---

### 2.10. Registro Estadístico y Telemetría Histórica
- **RF-STATS-01 (Métricas Almacenadas en Room DB)**:
  - Distancia total recorrida y tiempo en movimiento vs. tiempo detenido.
  - Velocidad media, velocidad máxima y gráfica de velocidad a lo largo del tiempo.
  - Condiciones climáticas registradas durante la actividad (temperatura mínima/máxima, presencia de lluvia o viento).
  - Perfil de elevación real con desnivel positivo acumulado (+D) y negativo (-D).
- **RF-STATS-02 (Exportación)**:
  - Exportación de la actividad completa en formato GPX enriquecido con extensiones de velocidad, altitud y meteorología.

---

### 2.11. Diagnóstico GNSS, Compatibilidad GPX y Guía de Usuario Visual
- **RF-GNSS-01 (Monitorización de Satélites y Señal)**:
  - Pantalla dedicada de diagnóstico GNSS con radar skyplot de 360° (acimut y elevación), conteo de satélites visibles y usados en el Fix 3D, desglose por constelación (GPS, Galileo, GLONASS, BeiDou) e intensidad de señal CN0 ($\text{dB-Hz}$).
- **RF-GPX-COMPAT-01 (Importación GPX 1.1 / 1.2 Extendido, KML y GeoJSON)**:
  - Soporte completo para archivos GPX estándar (1.1) y variantes extendidas con puntos intermedios (`<trp:ViaPoint/>`, `<trp:ShapingPoint/>`) y rutas encadenadas con extensiones `<gpxx:rpt>` sin generar líneas rectas erróneas entre segmentos.
- **RF-DOC-01 (Guía de Usuario con Capturas de Pantalla)**:
  - La guía de usuario oficial (`docs/user-guides/android-navigator/README.md`) **DEBE** incluir capturas de pantalla reales y actualizadas de la aplicación en funcionamiento (navegación Turn-by-Turn en mapa, gestión de rutas, mapas offline, radar de satélites GNSS y ajustes) almacenadas en `docs/user-guides/android-navigator/images/` y actualizadas siempre que cambie la interfaz.
- **RF-MAPS-HIERARCHY-01 (Mapas Offline Jerárquicos desde Ajustes)**:
  - La gestión de descarga de mapas offline es accesible exclusivamente desde la sección de Mapas dentro de Ajustes.
  - Navegación jerárquica de mayor a menor detalle (`Continente -> País -> Región`) con descarga masiva por país o continente completo, control de versiones instaladas con detección de actualizaciones y bloqueo opcional mediante preferencia **Solo Wi-Fi** frente a datos móviles.
- **RF-THEME-01 (Tema Claro/Oscuro y Estilos de Mapa a Color)**:
  - Configuración persistente de apariencia de interfaz (**Sistema / Sensor y Android Auto**, **Horas Crepusculares por posición GPS**, **Claro** y **Oscuro**), con cambio automático opcional del mapa al estilo nocturno al anochecer, múltiples paletas cartográficas a color (OSM Clásico, Pastel Suave, Rutas y Puertos, Topográfico y Nocturno Azul) y color del trazado de ruta.
- **RF-NAV-CTRL-01 (Ciclo de Vida de Navegación, Menú Hamburguesa y Barra Android)**:
  - Controles explícitos en pantalla para **Iniciar**, **Pausar**, **Reanudar** y **Parar** la navegación guiada; al entrar en navegación activa o en pausa, las pestañas inferiores se contraen a un **menú hamburguesa lateral (`☰`)** para maximizar el área visible del mapa.
  - Barra inferior de navegación del sistema Android con recorte estricto de viewport y modo configurable entre **Barra fija visible** o **Ocultación automática con gestos**.
- **RF-NAV-MOTION-01 (Orientación, Zoom por Velocidad y Recálculo Automático)**:
  - Rotación del mapa seleccionable en Ajustes y con acceso rápido sobre el mapa entre **Norte arriba** y **Seguimiento de dirección de marcha (Course-Up)**.
  - **Zoom dinámico según velocidad GPS**: mayor detalle en entorno urbano o curvas lentas y mayor anticipación a velocidades de carretera/autopista, con margen tras ajuste manual.
  - **Recálculo automático en marcha**: ante un desvío confirmado de la ruta activa durante la conducción real, la app recalcula la trayectoria respetando las preferencias de vía configuradas y las anuncia por voz sin bloquear al usuario.
- **RF-ROUTE-OPTS-01 (Preferencias de Vía, Favoritos y Compartición GPX)**:
  - Selector de criterios al calcular una ruta con destino: permitir o evitar **autovías/autopistas**, **peajes**, **ferries** y **caminos sin asfaltar**.
  - Gestión persistente de **destinos favoritos** (**Casa**, **Trabajo** y ubicaciones personalizadas) accesibles en un toque desde Ajustes y la búsqueda de destinos.
  - **Compartición bidireccional de GPX/KML/GeoJSON**: apertura directa o mediante la hoja *Compartir* del sistema (`ACTION_VIEW` / `ACTION_SEND`) cargando la ruta sobre el mapa lista para iniciar, y exportación/compartición de rutas guardadas hacia otras apps mediante `FileProvider`.
- **RF-TRAFFIC-DGT-01 (Capa Visual de Densidad de Tráfico por Colores)**:
  - Capa activable de tráfico e incidencias en tiempo real basada en fuentes abiertas (DATEX II v3.6 de la DGT) representada mediante código cromático (**Verde** fluido, **Ámbar** denso, **Naranja** intenso y **Rojo** retención/corte) sobre la ruta y su entorno cercano.

---

### 2.12. Principios UX/UI y Sistema de Diseño Material Design 3 (`m3.material.io`)
- **RF-UX-M3-01 (Reglas Tonales, Suavidad y Accesibilidad)**:
  - Todas las pantallas de navegación, rutas, mapas, diagnóstico GNSS y ajustes siguen estrictamente la especificación **Material Design 3** (`https://m3.material.io/`).
  - Paleta cromática basada en colores amables, suaves y equilibrados (familia principal **Azul Océano / Cielo M3** `#00506E` / `#87CFF9`, secundario azul pizarra suave `#23759B` / `#B0CADC` y acento terciario lavanda `#613A73` / `#E6B5F8`), evitando estéticas monocromas de alto contraste agresivo y prohibiendo el uso de marrón como color principal.
  - Superficies tonales graduadas con tarjetas flotantes redondeadas (`14–20 dp`), elevación suave, contraste mínimo WCAG AA en textos sobre fondo claro u oscuro y objetivos táctiles cómodos ($\ge 48\text{ dp}$).

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
- [ ] `test_offline_map_manager_agent_downloads_and_persists_packages_to_local_storage`
- [ ] `test_multi_agent_coordinator_prioritizes_safety_alerts_over_conversational_copilot`
