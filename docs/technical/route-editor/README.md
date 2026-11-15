# Documentación Técnica: Route & Track Editor

## 1. Visión Técnica
`route-editor` es la aplicación multiplataforma diseñada para crear, inspeccionar, editar y exportar tracks de navegación con soporte para múltiples formatos estándar (GPX, KML, GeoJSON).

---

## 2. Capacidades Técnicas Principales
- **Edición de Geometrías**:
  - Inserción, movimiento y eliminación de puntos de track (trackpoints).
  - División de tracks en múltiples segmentos y unión de tracks independientes.
  - Inversión de dirección del recorrido con recalculado temporal/altimétrico.
- **Procesamiento de Elevación**:
  - Cálculo de desniveles acumulados positivos (+D) y negativos (-D).
  - Suavizado de ruido altimétrico mediante filtros estadísticos.
  - Interfaz interactiva sincronizada entre el mapa y el gráfico de perfil altimétrico.
- **Enrutamiento Asistido (Auto-routing)**:
  - Soporte para ajuste automático a caminos y carreteras (vía integración con APIs de BRouter o motores OSRM).
  - Modo línea recta manual para tramos fuera de camino o senderismo libre.

---

## 3. Arquitectura y Módulos
- **Capa de Renderizado de Mapas**: Basada en mapas vectoriales con estilos personalizables y capas superpuestas (topográficas, satelitales, capas de calor).
- **Motor de Geometría**:
  - Algoritmo de Ramer-Douglas-Peucker para simplificación controlada de puntos.
  - Algoritmo Haversine / Vincenty para cálculo de distancias sobre esferoide terrestre.
- **Capa de Exportación / Importación**:
  - Serialización y deserialización conforme a especificaciones oficiales GPX 1.1 y GeoJSON (RFC 7946).
