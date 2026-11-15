# Arquitectura y Template de UI: Android Navigator

## 1. Filosofía de Diseño
La interfaz de navegación de `android-navigator` está diseñada con **Jetpack Compose (Material 3)** bajo tres premisas fundamentales:
1. **Legibilidad extrema en exteriores**: Alto contraste, tipografía clara y adaptación automática a modo nocturno.
2. **Operación ergonómica**: Controles principales accesibles en la zona inferior de la pantalla para uso con una sola mano.
3. **Cero distracciones**: Priorizar la información crítica de maniobras sobre elementos decorativos.

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
- **Estados**:
  - `ManeuverActive`: Muestra icono SVG de giro, distancia restante en metros/km y nombre de calle.
  - `Recalculating`: Indicador visual cuando el usuario se sale del track planificado por BRouter.
  - `Arrival`: Pantalla de destino alcanzado.

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
