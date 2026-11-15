# MB-navigator

Monorepo para el ecosistema de navegación y gestión de rutas Open Source **MB-navigator**.

## 📌 Visión del Proyecto

MB-navigator es una solución 100% de código abierto compuesta por:

1. **Android GPS Navigator**: Aplicación nativa Android enfocada en navegación GPS en tiempo real, mapas offline/online y seguimiento de rutas. Preparada y firmada para su distribución en Google Play Store.
2. **Route & Track Editor**: Aplicación multiplataforma (escritorio y web) para diseñar, inspeccionar, editar y exportar tracks en formatos estándar (GPX, KML, GeoJSON).
3. **Shared Core**: Modelos de datos compartidos, algoritmos de cálculo geométrico/altimétrico y parsers de rutas.

---

## 🏗️ Metodología de Desarrollo

### 1. SDD (Specification-Driven Development)
Antes de implementar cualquier módulo o funcionalidad, se redacta su especificación técnica formal en `docs/specs/`. Las fases incluyen:
- Definición de requisitos funcionales y no funcionales.
- Contratos de datos y esquemas de intercambio (GPX/GeoJSON).
- Casos de prueba previstos antes de la implementación.

### 2. TDD (Test-Driven Development)
- Ciclo estricto: **Red -> Green -> Refactor**.
- Cobertura de pruebas unitarias para lógica de negocio, parsers y cálculos de distancia/elevación.
- Pruebas de integración con fuentes simuladas de telemetría GPS.

---

## 🌿 Flujo de Trabajo (Git Flow)

Seguimos el modelo Git Flow:
- `main`: Código en producción, etiquetas de versión (`vX.Y.Z`).
- `develop`: Rama principal de integración para el siguiente release.
- `feature/*`: Nuevas características nacidas de `develop`.
- `release/*`: Preparación de versiones candidatas (pruebas finales, versionado).
- `hotfix/*`: Correcciones críticas directas desde `main`.

---

## 💬 Conventional Commits

Los commits deben respetar el estándar de [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

`<tipo>[ámbito opcional]: <descripción concisa>`

Tipos permitidos:
- `feat`: Nueva funcionalidad.
- `fix`: Corrección de errores.
- `docs`: Cambios en documentación o specs.
- `test`: Añadir o modificar tests (TDD).
- `refactor`: Refactorización de código sin cambio funcional.
- `ci`: Tareas de integración continua y despliegue.
- `chore`: Mantenimiento general, dependencias, etc.

---

## 📂 Estructura del Repositorio

```text
├── apps/
│   ├── android-navigator/    # App nativa Android (GPS / Navegación)
│   └── route-editor/         # App multiplataforma (Edición de rutas)
├── shared/                   # Lógica y modelos compartidos
├── docs/
│   ├── specs/                # Especificaciones SDD
│   ├── adr/                  # Architecture Decision Records
│   └── release-guide.md      # Guía de firma y release en Play Store
├── .editorconfig
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```
