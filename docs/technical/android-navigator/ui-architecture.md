# Arquitectura y Template de UI: Android Navigator

## 1. Filosofía de Diseño
La interfaz de navegación de `android-navigator` está diseñada con **Jetpack Compose (Material 3)** con una estética deportiva de alta gama (estilo Garmin / Apple Watch Ultra), bajo tres premisas fundamentales:
1. **Legibilidad extrema en exteriores**: Contraste OLED negro puro (`#000000`) con acentos de alta luminosidad (verde neón `#00E676`, naranja `#FF6D00` y cian `#00E5FF`).
2. **Glassmorphism translúcido**: Integración de **Haze** para desenfoque en tiempo real (*frosted glass*) sobre el mapa en movimiento.
3. **Micro-animaciones vectoriales con Lottie**: Integración de **Lottie Compose** para transiciones fluidas de giros, búsqueda de satélites y alertas sin bloquear el renderizado del mapa.
4. **Tipografía técnica de instrumentación**: Uso de fuentes monoespaciadas (JetBrains Mono / Geist Mono) para telemetría (velocidad, altitud, distancia, rumbo).

---

## 2. Diagrama del Layout Principal (Modo Navegación Activa)

```text
+-------------------------------------------------------------+
| [<-]  En 250 m: Gira a la derecha (C/ Mayor)       [120m]   | <- TurnByTurnHUD
|       Luego: Sigue recto 1.2 km                             |
+-------------------------------------------------------------+
|                                                             |
|                                        [ (N) Brújula ]      | <- FloatingControls
|                                        [ [3D] Perspectiva ] |
|                                        [ [::] Capas mapa ]  |
|                         [MAPA]                              |
|                    (MapLibre Compose)                       |
|                                                             |
|  ( 65 km/h )                                                | <- SpeedometerOverlay
|  Velocidad actual                      [ (O) Recentrar GPS ]|
+-------------------------------------------------------------+
| ^ Deslizar arriba para ver detalles                         | <- TripProgressSheet
| [ 14:35 ] ETA    |    [ 18.4 km ] Restante   |   [ 22 min ] |
+-------------------------------------------------------------+
```

---

## 3. Especificación de Componentes Compose

### `TurnByTurnHUD`
- **Ubicación**: Anclado en la parte superior (`Alignment.TopCenter`).
- **Animaciones Lottie (`LottieAnimation`)**:
  - `turn_maneuver.json`: Animación vectorial dinámica del giro según la distancia decreciente.
  - `gps_searching_radar.json`: Radar orbital animado durante la adquisición inicial de satélites GPS.
  - `off_route_pulse.json`: Pulso sutil de advertencia en caso de desvío del track.
- **Estados**:
  - `ManeuverActive`: Muestra icono vectorial animado con Lottie, distancia decreciente en metros/km y nombre de calle.
  - `Recalculating`: Indicador visual cuando el usuario se sale del track planificado por BRouter.
  - `Arrival`: Celebración y resumen al alcanzar el destino.

### `FloatingControls`
- **Columna lateral derecha**:
  - `CompassButton`: Alterna entre rotación libre según rumbo de avance (*Heading Up*) y bloqueo hacia el norte (*North Up*).
  - `TiltToggle`: Alterna entre perspectiva 2D plana y 3D inclinada (45º).
  - `LayersButton`: Despliega un menú inferior para alternar entre estilos de mapa (Topográfico, Callejero OSM, Satélite).
  - `RecenterFAB`: Devuelve el mapa a la posición actual del usuario con seguimiento continuo activado.

### `TripProgressSheet`
- Implementado mediante `ModalBottomSheet` o `BottomSheetScaffold` de Material 3.
- **Vista Colapsada**: Hora estimada de llegada (ETA), distancia restante, tiempo estimado y botón de cancelación rápida.
- **Vista Expandida**:
  - Perfil altimétrico con la posición actual del usuario marcada en la gráfica.
  - Lista paso a paso de maniobras pendientes (*Roadbook*).
  - Métricas de telemetría: velocidad media, altitud actual, desnivel positivo acumulado y estado de precisión del GPS.
