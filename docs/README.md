# Índice de Documentación - MB-navigator

Bienvenido al centro de documentación del ecosistema **MB-navigator**. Toda la documentación técnica, arquitectónica y guías para el usuario final se mantienen versionadas en este directorio.

---

## 🗂️ Estructura de Documentación

### 1. Documentación Técnica (`/docs/technical/`)
Destinada a desarrolladores, colaboradores y mantenimiento del software.
- [Android Navigator](file:///Users/carlos/Projects/my-nav/docs/technical/android-navigator/README.md): Arquitectura nativa (Kotlin, Compose, Clean Architecture), gestión de servicios en segundo plano, consumo de batería, proveedor de localización desacoplado para TDD.
- [Route Editor](file:///Users/carlos/Projects/my-nav/docs/technical/route-editor/README.md): Arquitectura multiplataforma, motor de renderizado geográfico, algoritmos de cálculo de altimetría y simplificación de geometrías.
- [Shared Core](file:///Users/carlos/Projects/my-nav/docs/technical/shared-core/README.md): Estándares de formato de datos (GPX 1.1, GeoJSON, KML), modelos de datos comunes y utilidades matemáticas.

---

### 2. Guías de Usuario (`/docs/user-guides/`)
Manuales prácticos y tutoriales paso a paso orientados al usuario final.
- [Guía de Usuario - Android Navigator](file:///Users/carlos/Projects/my-nav/docs/user-guides/android-navigator/README.md): Instalación, configuración de mapas sin conexión, grabación de tracks, navegación paso a paso e importación/exportación de rutas.
- [Guía de Usuario - Route Editor](file:///Users/carlos/Projects/my-nav/docs/user-guides/route-editor/README.md): Cómo crear una ruta desde cero, edición de perfiles de elevación, unión y división de tracks, y exportación a dispositivos GPS.

---

### 3. Especificaciones SDD (`/docs/specs/`)
Especificaciones formales requeridas antes de codificar cada módulo funcional según la metodología SDD.
- [Plantilla de Especificación](file:///Users/carlos/Projects/my-nav/docs/specs/template.md)

---

### 4. Decisiones de Arquitectura (`/docs/adr/`)
Registro histórico de decisiones técnicas relevantes (Architecture Decision Records).
- [ADR 0001: Estructura Monorepo](file:///Users/carlos/Projects/my-nav/docs/adr/0001-monorepo-structure.md)

---

### 5. Guías de Despliegue y Release
- [Guía de Firma y Release en Google Play Store](file:///Users/carlos/Projects/my-nav/docs/release-guide.md)

---

## ✍️ Política de Mantenimiento de la Documentación

Cada pull request que introduzca una nueva funcionalidad o altere el comportamiento de una aplicación debe:
1. Actualizar la especificación correspondiente en `docs/specs/`.
2. Actualizar la documentación técnica en `docs/technical/<app>/`.
3. Actualizar la guía de usuario en `docs/user-guides/<app>/` si el cambio impacta en la interfaz o flujo del usuario.
