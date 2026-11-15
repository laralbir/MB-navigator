# SPEC-[NUM]: [Título de la Especificación]

- **Estado**: [Borrador | En Revisión | Aprobado | Implementado | Obsoleto]
- **App de destino**: [Android Navigator | Multiplatform Route Editor | Shared Core]
- **Fecha**: AAAA-MM-DD
- **Autores**: [Nombre/GitHub]

---

## 1. Resumen Ejecutivo
Descripción concisa del problema que se aborda y la solución propuesta.

## 2. Requisitos Funcionales
- **RF-1**: El sistema debe...
- **RF-2**: El usuario podrá...

## 3. Requisitos No Funcionales
- **RNF-1 (Rendimiento)**: Consumo de CPU, memoria, tasa de refresco GPS (1Hz / 5Hz).
- **RNF-2 (Disponibilidad)**: Funcionamiento offline sin conectividad de red.
- **RNF-3 (Privacidad)**: Almacenamiento local de rutas sin telemetría externa no consentida.

## 4. Arquitectura y Contratos de Datos
Diagramas de flujo, esquemas de clases o definiciones JSON / Protobuf / GPX.

```json
{
  "trackId": "uuid-v4",
  "name": "Ruta de prueba",
  "points": []
}
```

## 5. Casos de Prueba TDD (Test Cases)
Lista de casos de prueba unitarios e integrados que deben implementarse antes del código de producción:
- [ ] `test_valid_gpx_parsing_produces_correct_waypoint_count`
- [ ] `test_invalid_coordinates_throw_validation_error`
- [ ] `test_gps_loss_triggers_fallback_reconnect_state`

## 6. Criterios de Aceptación
Condiciones requeridas para considerar la especificación completada y lista para merge en `develop`.
