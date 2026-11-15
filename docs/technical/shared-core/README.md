# Documentación Técnica: Shared Core

## 1. Alcance
`shared-core` contiene la lógica de dominio agnóstica de la plataforma:
- Modelos canónicos de rutas, tracks, segmentos y waypoints.
- Validadores de esquemas e integridad de datos geográficos.
- Funciones matemáticas de georreferenciación y telemetría.

---

## 2. Modelo de Datos Canónico

Todas las aplicaciones del ecosistema operan internamente sobre esta estructura neutral antes de exportar a GPX, KML o GeoJSON:

```typescript
interface GeoPoint {
  latitude: number;      // -90 a 90
  longitude: number;     // -180 a 180
  elevation?: number;    // Metros sobre el nivel del mar
  timestamp?: number;    // Epoch en milisegundos (UTC)
  speed?: number;        // Metros por segundo (opcional)
  heading?: number;      // Grados (0 a 359)
}

interface Waypoint extends GeoPoint {
  id: string;
  name: string;
  description?: string;
  symbol?: string;       // Categoría o icono asociado
}

interface TrackSegment {
  points: GeoPoint[];
}

interface Track {
  id: string;
  title: string;
  description?: string;
  createdDate: number;
  segments: TrackSegment[];
  waypoints: Waypoint[];
  statistics: {
    totalDistanceMeters: number;
    elevationGainMeters: number;
    elevationLossMeters: number;
    durationSeconds?: number;
  };
}
```

---

## 3. Algoritmos Compartidos
1. **Haversine**: Cálculo de distancia entre dos coordenadas geográficas sobre la esfera de radio promedio 6.371.000 metros.
2. **Douglas-Peucker**: Reducción del número de puntos de un track manteniendo la fidelidad visual y métrica con un umbral de tolerancia configurable ($\epsilon$).
3. **Cálculo de Desnivel Filtrado**: Umbral mínimo de cambio de altitud (ej. 3 metros) para evitar acumular ruido barométrico o errores de precisión vertical del GPS.
