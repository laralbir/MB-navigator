# Arquitectura y Template de UI: Route & Track Editor

## 1. Filosofía de Diseño
La interfaz de `route-editor` (Flutter Desktop y Web) sigue un patrón de **espacio de trabajo GIS / CAD**, optimizado para el uso ágil del ratón, atajos de teclado y visualización simultánea del mapa y el perfil altimétrico.

### Stack UI Seleccionado:
- **Diseño y Componentes**: **`shadcn_ui` (Flutter)** para una estética desktop sobria, moderna y refinada.
- **Iconografía**: **`lucide_icons`** (iconos vectoriales nítidos y coherentes).
- **Layout Ajustable**: **`multi_split_view`** para paneles redimensionables con el ratón (sidebar y panel inferior altimétrico).
- **Animaciones y Micro-interacciones**: **`flutter_animate`** y **`flutter_map_animations`** (transiciones de cámara cinemáticas y trazado láser de polilíneas).
- **Entrada de Datos Nativa**: **`super_drag_and_drop`** para arrastrar archivos `.gpx`/`.kml` desde el explorador del sistema operativo al lienzo del mapa.
- **Gráficos Altimétricos**: **`fl_chart`** con gradientes y tooltips interactivos.

---

## 2. Diagrama del Layout Principal (Desktop & Web Workspace)

```text
+---------------------------------------------------------------------------------------+
| [Logo] MB-navigator | [Nuevo] [Abrir GPX] [Guardar] | [<- Deshacer] [Rehacer ->]      | <- TopMenuBar
+-------------------------+-------------------------------------------------------------+
| HERRAMIENTAS            |                                                             |
| (*) Auto-routing        |                                                             |
| ( ) Línea recta         |                                        [ + ] Zoom           |
| ( ) Cortar / Dividir    |                                        [ - ]                |
| ( ) Invertir sentido    |                         [CANVAS]                            |
| ----------------------- |                    (flutter_map)       [ Capas ]            |
| PERFIL RUTEO            |                                        (OSM / Topo / Sat)   |
| [ Senderismo  v ]       |                                                             |
| ----------------------- |                                                             |
| WAYPOINTS / TRAMOS      |                                                             |
| 1. Inicio (0.0 km)      |                                                             |
| 2. Collado (4.2 km)     |                                                             |
| 3. Cumbre (7.8 km)      |                                                             |
+-------------------------+-------------------------------------------------------------+
| PERFIL DE ELEVACIÓN                                                       [ _ ] [ ^ ] | <- ElevationPanel
| [ Gráfico interactivo fl_chart con altitud (m) y distancia (km) ]                     |    (Plegable)
| Distancia: 14.2 km | Desnivel +: 620 m | Desnivel -: 610 m | Pendiente actual: 8.5%   |
+---------------------------------------------------------------------------------------+
```

---

## 3. Especificación de Componentes Flutter

### `TopMenuBar`
- **Barra de navegación principal**:
  - Acciones rápidas de archivo: `Nuevo`, `Abrir (.gpx, .kml, .geojson)`, `Guardar / Exportar`.
  - Historial de estados (BLoC): Botones interactivos de `Deshacer` (`Ctrl+Z` / `Cmd+Z`) y `Rehacer` (`Ctrl+Y` / `Cmd+Shift+Z`).
  - Nombre editable de la ruta activa.

### `SidebarToolbox`
- **Barra lateral izquierda**:
  - **Selector de modo de dibujo**:
    - *Auto-routing*: Generación automática de ruta ajustada a caminos mediante BRouter.
    - *Línea recta*: Inserción directa de vértices sin seguir caminos.
    - *Edición de puntos*: Permite seleccionar y arrastrar vértices (`DragMarker`).
    - *Tijeras*: División del track en dos segmentos.
  - **Selector de perfil**: Senderismo, Bicicleta de montaña (MTB), Bicicleta de carretera.
  - **Lista de Waypoints**: Lista reordenable (*ReorderableListView*) con nombre, elevación y botón de borrado individual.

### `MapCanvas`
- Basado en **`flutter_map`**.
- Soporte para capas base: OpenStreetMap estándar, OpenTopoMap, imágenes satelitales y capas de cicloturismo (CyclOSM).
- Cursor dinámico sincronizado bidireccionalmente con el panel de elevación.

### `ElevationPanel`
- Gráfico interactivo construido con **`fl_chart`**.
- Curva altimétrica sombreada con código de colores según el gradiente de pendiente:
  - Verde: < 5% (llano / falso llano)
  - Amarillo: 5% - 10% (pendiente moderada)
  - Rojo: 10% - 15% (pendiente pronunciada)
  - Negro: > 15% (rampa extrema)
- Al mover el cursor del ratón sobre la gráfica, se proyecta un marcador circular sobre el punto exacto de la ruta en el mapa.
