# Documentación Técnica: Android Navigator

## 1. Visión Técnica
`android-navigator` es la aplicación nativa para Android de MB-navigator. Su objetivo principal es ofrecer navegación GPS precisa, confiable y con soporte completo sin conexión (offline), optimizada para el consumo de batería y la privacidad del usuario.

---

## 2. Pila Tecnológica (Tech Stack)
- **Lenguaje**: Kotlin (versión más reciente con soporte coroutines y Flow).
- **Interfaz**: Jetpack Compose con Material Design 3.
- **Arquitectura**: Clean Architecture + MVI (Model-View-Intent).
- **Inyección de Dependencias**: Hilt / Koin.
- **Motor de Mapas**: MapLibre Native SDK for Android / `maplibre-compose`.
- **Base de Datos Local**: Room (para waypoints, rutas almacenadas y metadatos de mapas descargados).
- **Procesamiento de Ubicación**: Abstracción sobre `LocationManager` y `FusedLocationProviderClient`.

---

## 3. Principios de Diseño para TDD

Para cumplir con Test-Driven Development (TDD) en un contexto de hardware móvil:

```text
                  +---------------------------+
                  |   LocationTracker (IF)    |
                  +---------------------------+
                                ^
                                |
             +------------------+------------------+
             |                                     |
+--------------------------+             +--------------------------+
| RealLocationTracker      |             | MockLocationTracker      |
| (GPS Hardware / Android) |             | (Lee archivos GPX/JSON)  |
+--------------------------+             +--------------------------+
             |                                     |
      Modo Producción                         Modo Pruebas TDD
```

- **Inyección del Proveedor de Ubicación**: La lógica de navegación, cálculo de desviaciones de ruta y alertas de giro consumen la interfaz `LocationTracker`.
- **Simulador de Rutas para Tests**: Los tests unitarios e integrados reproducen telemetría simulada (latitud, longitud, altitud, precisión, velocidad y tiempo) sin requerir emuladores con GPS activo ni dispositivos físicos.

---

## 4. Gestión en Segundo Plano y Batería
- **Foreground Service**: El seguimiento de actividad o grabación de tracks se ejecuta mediante un servicio en primer plano con notificación permanente (`android.permission.FOREGROUND_SERVICE_LOCATION`).
- **Wakelocks y Doze Mode**: Gestión eficiente para registrar ubicaciones con intervalos configurables (1s para alta precisión, 5s o basada en distancia para senderismo de larga duración).
