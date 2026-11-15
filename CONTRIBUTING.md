# Guía de Contribución - MB-navigator

Gracias por contribuir a **MB-navigator**. Este proyecto es 100% de código abierto y sigue estándares rigurosos de ingeniería de software.

---

## 1. Convención de Ramas (Git Flow)

- **`main`**: Refleja siempre el estado listo para producción. Cada commit en `main` debe corresponder a una release etiquetada con Semantic Versioning (`v1.0.0`).
- **`develop`**: Rama base para el desarrollo diario. Se integra mediante Pull Requests revisados.
- **`feature/<nombre-descriptivo>`**: Creada desde `develop`. Incorpora una funcionalidad o especificación específica.
- **`release/<version>`**: Se genera desde `develop` para congelar funcionalidades, ajustar números de versión y preparar las notas de lanzamiento antes de fusionar en `main` y `develop`.
- **`hotfix/<nombre>`**: Creada directamente desde `main` para solucionar fallos urgentes en producción.

---

## 2. Formato de Commits (Conventional Commits 1.0.0)

Estructura requerida:

```text
<tipo>(<alcance opcional>): <descripción imperativa en presente>

[cuerpo explicativo opcional]

[pie de página con referencias a issues / breaking changes opcional]
```

### Ejemplos:
- `feat(gps): add background location tracking service`
- `test(parser): add test cases for corrupted gpx files`
- `fix(map): prevent memory leak when rotating device`
- `docs(specs): add specification 002 for track elevation profile`

---

## 3. Metodología SDD (Specification-Driven Development)

1. Antes de codificar, crea o consulta la especificación en `docs/specs/`.
2. Asigna un identificador correlativo (por ejemplo `SPEC-001-gps-tracking.md`).
3. Define los contratos de entrada/salida, estados posibles y criterios de aceptación.
4. Obtén aprobación del diseño antes de proceder a la implementación.

---

## 4. Metodología TDD (Test-Driven Development)

1. **Rojo**: Escribe un test unitario que falle basado en los criterios de la especificación SDD.
2. **Verde**: Escribe la mínima cantidad de código necesario para que el test pase satisfactoriamente.
3. **Refactor**: Mejora la legibilidad y estructura del código asegurando que toda la suite de pruebas se mantenga en verde.
