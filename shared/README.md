# Shared Core: Schemas y Modelos Canónicos

Este directorio almacena los contratos de datos agnósticos de la plataforma y esquemas de validación de **MB-navigator**.

## 1. Esquema Canónico de Track (`canonical_track.schema.json`)
Define la estructura JSON neutral para representar rutas, segmentos, waypoints obligatorios y telemetría calculada. Ambas aplicaciones (`apps/android-navigator` y `apps/route-editor`) mapean internamente sus clases de dominio a esta especificación.

## 2. Compatibilidad de Formatos Soportados
- **GPX 1.1**: Formato estándar de intercambio XML (`<trk>`, `<trkseg>`, `<trkpt>`, `<wpt>`).
- **GeoJSON (RFC 7946)**: Geometrías `LineString`, `MultiLineString` y `FeatureCollection`.
- **KML 2.2**: Estándar OGC para representación cartográfica.
