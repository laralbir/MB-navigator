# Guía de Usuario: Route & Track Editor

Esta guía te muestra cómo utilizar **MB-navigator Route Editor** para planificar, limpiar y perfeccionar tus rutas de senderismo, ciclismo o carretera antes de salir a la aventura.

---

## 1. Interfaz Principal

La pantalla principal se divide en tres áreas clave:
- **Área Central (Mapa)**: Muestra el mapa interactivo donde puedes dibujar o visualizar tus rutas.
- **Panel Inferior (Perfil de Elevación)**: Muestra la gráfica altimétrica, pendiente porcentual y distancia recorrida. Al mover el cursor sobre la gráfica, se resalta la posición exacta en el mapa.
- **Barra Lateral de Herramientas**: Opciones de edición, unión, recorte y exportación.

---

## 2. Crear una Ruta desde Cero

1. Pulsa en **Nueva Ruta**.
2. Selecciona el modo de trazado:
   - **Ajuste automático (Auto-routing)**: El trazado se adherirá automáticamente a caminos, carreteras o senderos transitables según el perfil elegido (senderismo, bicicleta de montaña, carretera).
   - **Línea recta (Manual)**: Permite trazar segmentos libres punto a punto para pasos fuera de pista o campo a través.
3. Haz clic sucesivamente en el mapa para marcar los puntos de paso.
4. Si cometes un error, puedes deshacer el último punto pulsando `Ctrl+Z` (o `Cmd+Z` en macOS).

---

## 3. Limpieza y Edición de Tracks Existentes

1. Arrastra y suelta un archivo `.gpx`, `.kml` o `.geojson` sobre la ventana de la aplicación.
2. **Dividir un track**: Haz clic sobre cualquier punto del recorrido y selecciona la herramienta *Dividir*. El track se segmentará en dos partes independientes.
3. **Unir tracks**: Selecciona dos rutas cargadas y pulsa *Unir*. La aplicación conectará el final de la primera con el inicio de la segunda.
4. **Invertir sentido**: Si deseas realizar la ruta en sentido contrario, selecciona la herramienta *Invertir dirección*.

---

## 4. Exportar a Dispositivos y Aplicaciones

1. Cuando la ruta esté lista, pulsa en el botón **Exportar**.
2. Elige el formato adecuado:
   - **GPX**: Ideal para la aplicación MB-navigator Android, dispositivos Garmin, Wahoo o Suunto.
   - **KML**: Recomendado para visualizar en Google Earth.
   - **GeoJSON**: Útil para análisis geoespacial o compartir en plataformas web.
3. Opcionalmente, activa la casilla **Optimizar número de puntos** para reducir el peso del archivo sin perder precisión geométrica.
