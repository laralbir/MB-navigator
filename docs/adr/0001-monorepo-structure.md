# ADR 0001: Estructura Monorepo para MB-navigator

- **Fecha**: 2026-11-15
- **Estado**: Aceptado

## Contexto
El ecosistema MB-navigator consta de múltiples aplicaciones y bibliotecas vinculadas:
1. Navegador nativo Android.
2. Editor de rutas multiplataforma.
3. Componentes y parsers compartidos (formatos GPX/KML/GeoJSON, matemáticas de distancias).

Mantener repositorios separados aumentaría la fricción en la sincronización de modelos de datos, versiones de especificaciones y control de versiones.

## Decisión
Adoptar una arquitectura de monorepo con la siguiente división:
- `apps/android-navigator`: Aplicación nativa Android (Kotlin / Jetpack Compose).
- `apps/route-editor`: Aplicación multiplataforma de escritorio/web.
- `shared`: Lógica de negocio compartida (parsers y utilidades geométricas).
- `docs`: Documentación viva, ADRs y especificaciones SDD centralizadas.

## Consecuencias
- **Positivas**:
  - Un único flujo de Git Flow y Conventional Commits centralizado.
  - Trazabilidad directa entre especificaciones SDD y el código en todas las plataformas.
  - Facilidad para compartir esquemas y pruebas de integración.
- **A tener en cuenta**:
  - La configuración de CI/CD debe ejecutar pipelines selectivos según los archivos modificados.
