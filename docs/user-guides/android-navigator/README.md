# Guía de Usuario: Android Navigator

Bienvenido a la guía de usuario de **MB-navigator para Android**. Esta aplicación te permite navegar en tiempo real y seguir tus rutas en cualquier lugar, incluso sin cobertura telefónica ni conexión a Internet.

---

## 1. Instalación y Primer Inicio

1. Descarga e instala la aplicación desde Google Play Store o desde la sección de lanzamientos (*Releases*) en GitHub.
2. Al abrir la app por primera vez, se solicitarán permisos esenciales:
   - **Ubicación exacta**: Necesario para posicionar el mapa y guiarte en ruta.
   - **Ubicación en segundo plano (opcional)**: Solo se requiere si deseas continuar grabando tu ruta con la pantalla apagada o mientras usas otra aplicación.
   - **Notificaciones**: Para mostrar los controles rápidos de grabación y avisos de giro.

---

## 2. Descarga de Mapas sin Conexión (Offline)

Para utilizar la aplicación en zonas montañosas o sin cobertura:
1. Dirígete al menú lateral y pulsa en **Gestor de Mapas**.
2. Selecciona tu país o región de interés.
3. Pulsa en **Descargar**. Una vez completada la descarga, el mapa estará disponible de forma permanente sin necesidad de gastar datos móviles.

---

## 3. Grabación de un Nuevo Recorrido

1. En la pantalla principal, presiona el botón circular rojo de **Grabar**.
2. La aplicación comenzará a registrar tus coordenadas, velocidad, distancia y altimetría.
3. Puedes pausar la grabación en cualquier momento o añadir **Puntos de Interés (Waypoints)** pulsando en el icono de chincheta.
4. Al finalizar, pulsa en **Detener y Guardar**. Asigna un nombre a tu recorrido y este quedará guardado en tu biblioteca local.

---

## 4. Importar y Seguir una Ruta (GPX / KML)

1. Ve a **Mis Rutas** > **Importar**.
2. Selecciona el archivo `.gpx`, `.kml` o `.geojson` desde el explorador de archivos de tu dispositivo o compártelo directamente desde tu aplicación de mensajería hacia MB-navigator.
3. Puedes añadir o reordenar **Puntos de Interés (Waypoints obligatorios)** por los que la ruta debe pasar necesariamente.
4. Pulsa sobre la ruta y selecciona **Iniciar Navegación**.
5. La pantalla orientará el mapa automáticamente en la dirección de avance y te alertará si te desvías del trazado planificado.

---

## 5. Compatibilidad con Android Auto

1. Conecta tu teléfono mediante cable USB o conexión inalámbrica a la unidad compatible con **Android Auto** de tu vehículo.
2. Abre **MB-navigator** directamente desde la pantalla táctil de tu salpicadero.
3. La interfaz se adaptará automáticamente al formato vehicular (`NavigationTemplate`), permitiendo:
   - Control de maniobras sin distracciones con los mandos físicos del volante o la pantalla táctil.
   - Resumen del próximo giro en el panel de instrumentos (si el vehículo lo soporta).

---

## 6. Meteorología por Tramo y Alertas en Ruta (AEMET & Open-Meteo)

Durante la planificación o seguimiento de una ruta:
1. La aplicación calcula tu **hora estimada de paso** por cada tramo del itinerario.
2. Consulta en tiempo real las condiciones previstas para ese instante exacto (temperatura, lluvia, viento y niebla con Open-Meteo).
3. Si existe una alerta oficial de AEMET activa:
   - **Aviso Amarillo**: Indicador visual informativo.
   - **Aviso Naranja / Rojo**: Alerta prioritaria por voz y sugerencia en pantalla de una ruta alternativa más segura (por ejemplo, evitando puertos de montaña con riesgo de nevadas).

---

## 7. Tráfico, Radares e Incidencias en Vivo (DGT 3.0)

- **Balizas V16 y Vehículos Detenidos**: Conexión directa con la plataforma DGT 3.0 para avisarte con suficiente antelación de obstáculos o vehículos averiados en tu carril o arcén.
- **Radares**: Advertencias visuales y sonoras progresivas de radares fijos, de tramo y zonas de control habitual.
- **Límites de Velocidad**: Indicador visual permanente del límite legal de la vía (`maxspeed`) y aviso configurable si se supera.

---

## 8. Asistente Inteligente con IA (Google Gemini - Opcional)

En **Ajustes > Inteligencia Artificial**, puedes elegir:
- **Modo Offline Puro**: Sin envío de datos externos; el navegador funciona de forma 100% privada con reglas internas.
- **Modo Gemini API**: Introduce tu propia API Key para disponer de un copiloto conversacional que puede:
  - Resumirte las condiciones del viaje antes de salir.
  - Responder a preguntas complejas por voz (*"¿Dónde puedo parar a cargar gasolina antes de que empiece a llover?"*).

