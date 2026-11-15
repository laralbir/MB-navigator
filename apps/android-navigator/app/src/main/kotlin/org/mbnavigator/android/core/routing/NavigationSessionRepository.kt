package org.mbnavigator.android.core.routing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mbnavigator.android.core.geo.HaversineDistanceCalculator
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.model.Waypoint
import java.io.File

data class SavedRouteEntry(
    val id: String,
    val title: String,
    val distanceKm: Double,
    val elevationGainM: Double,
    val curveCount: Int,
    val estimatedCurviness: String,
    val format: String,
    val dateAdded: String,
    val points: List<GeoPoint>,
    val waypoints: List<Waypoint> = emptyList()
)

data class ActiveRouteSession(
    val id: String,
    val title: String,
    val points: List<GeoPoint>,
    val maneuvers: List<RouteManeuver>,
    val waypoints: List<Waypoint> = emptyList(),
    val isPreviewSimulation: Boolean = false
)

enum class NavigationRunState {
    IDLE,
    READY,
    NAVIGATING,
    PAUSED
}

data class SharedRouteImportEvent(
    val timestampMs: Long,
    val success: Boolean,
    val message: String,
    val importedEntry: SavedRouteEntry? = null
)

/**
 * Repositorio centralizado (singleton por sesión de app) que conecta
 * la biblioteca de rutas GPX/KML, el calculador de rutas en carretera y el mapa GPS activo.
 */
object NavigationSessionRepository {

    private var routesDir: File? = null
    private var initialized = false

    private val _savedRoutes = MutableStateFlow<List<SavedRouteEntry>>(emptyList())
    val savedRoutes: StateFlow<List<SavedRouteEntry>> = _savedRoutes.asStateFlow()

    private val _activeRoute = MutableStateFlow<ActiveRouteSession?>(null)
    val activeRoute: StateFlow<ActiveRouteSession?> = _activeRoute.asStateFlow()

    private val _navigationState = MutableStateFlow(NavigationRunState.IDLE)
    val navigationState: StateFlow<NavigationRunState> = _navigationState.asStateFlow()

    private val _sharedImportEvent = MutableStateFlow<SharedRouteImportEvent?>(null)
    val sharedImportEvent: StateFlow<SharedRouteImportEvent?> = _sharedImportEvent.asStateFlow()

    fun ensureInitialized(context: Context) {
        if (initialized) return
        initialized = true
        routesDir = File(context.filesDir, "saved_routes").apply { if (!exists()) mkdirs() }

        val builtIn = createBuiltInMotoRoutes()
        val imported = loadPersistedImportedRoutes()
        _savedRoutes.value = imported + builtIn
    }

    fun startNavigatingRoute(
        route: SavedRouteEntry,
        previewSimulation: Boolean = false,
        autoStart: Boolean = true
    ) {
        val maneuvers = TurnByTurnEngine.extractManeuversFromTrack(route.points, route.title)
        _activeRoute.value = ActiveRouteSession(
            id = route.id,
            title = route.title,
            points = route.points,
            maneuvers = maneuvers,
            waypoints = route.waypoints,
            isPreviewSimulation = previewSimulation
        )
        _navigationState.value = if (autoStart) NavigationRunState.NAVIGATING else NavigationRunState.READY
    }

    fun startCalculatedRoute(
        result: CalculatedRouteResult,
        waypoints: List<Waypoint> = emptyList(),
        autoStart: Boolean = false
    ) {
        _activeRoute.value = ActiveRouteSession(
            id = "calc-${System.currentTimeMillis()}",
            title = result.title,
            points = result.points,
            maneuvers = result.maneuvers,
            waypoints = waypoints,
            isPreviewSimulation = false
        )
        _navigationState.value = if (autoStart) NavigationRunState.NAVIGATING else NavigationRunState.READY
    }

    fun startNavigation() {
        if (_activeRoute.value != null) {
            _navigationState.value = NavigationRunState.NAVIGATING
        }
    }

    fun pauseNavigation() {
        if (_navigationState.value == NavigationRunState.NAVIGATING) {
            _navigationState.value = NavigationRunState.PAUSED
        }
    }

    fun resumeNavigation() {
        if (_activeRoute.value != null && _navigationState.value == NavigationRunState.PAUSED) {
            _navigationState.value = NavigationRunState.NAVIGATING
        }
    }

    fun stopNavigation() {
        _navigationState.value = if (_activeRoute.value != null) {
            NavigationRunState.READY
        } else {
            NavigationRunState.IDLE
        }
    }

    fun clearActiveRoute() {
        _activeRoute.value = null
        _navigationState.value = NavigationRunState.IDLE
    }

    fun importRouteFromUri(context: Context, uri: Uri): Result<SavedRouteEntry> {
        ensureInitialized(context)
        return try {
            val fileName = resolveFileName(context, uri)
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return Result.failure(IllegalStateException("No se pudo leer el archivo seleccionado"))

            val parsed = GpxKmlParser.parse(content, fileName.substringBeforeLast("."))
            if (parsed.points.isEmpty()) {
                return Result.failure(IllegalArgumentException("El archivo no contiene puntos GPS válidos"))
            }

            val id = "imp_${System.currentTimeMillis()}"
            // Guardar copia local para persistir entre reinicios
            val targetFile = File(routesDir, "$id.${fileName.substringAfterLast('.', "gpx")}")
            targetFile.writeText(content)

            val curvinessLabel = when {
                parsed.curveCount >= 120 -> "Extrema (${parsed.curveCount} curvas)"
                parsed.curveCount >= 40 -> "Muy Alta (${parsed.curveCount} curvas)"
                parsed.curveCount >= 10 -> "Alta (${parsed.curveCount} curvas)"
                else -> "Suave (${parsed.curveCount} curvas)"
            }

            val entry = SavedRouteEntry(
                id = id,
                title = parsed.title,
                distanceKm = parsed.distanceKm,
                elevationGainM = parsed.elevationGainMeters,
                curveCount = parsed.curveCount,
                estimatedCurviness = curvinessLabel,
                format = parsed.format,
                dateAdded = "Recién importado",
                points = parsed.points,
                waypoints = parsed.waypoints
            )

            _savedRoutes.value = listOf(entry) + _savedRoutes.value
            Result.success(entry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Importa un archivo GPX/KML/GeoJSON compartido o abierto desde otra app (Intent VIEW/SEND)
     * y lo deja preparado en pantalla listo para iniciar navegación.
     */
    fun importSharedRouteUri(context: Context, uri: Uri): Result<SavedRouteEntry> {
        val res = importRouteFromUri(context, uri)
        res.fold(
            onSuccess = { entry ->
                startNavigatingRoute(entry, previewSimulation = false, autoStart = false)
                _sharedImportEvent.value = SharedRouteImportEvent(
                    timestampMs = System.currentTimeMillis(),
                    success = true,
                    message = "Ruta recibida: ${entry.title} (${entry.points.size} pts)",
                    importedEntry = entry
                )
            },
            onFailure = { err ->
                _sharedImportEvent.value = SharedRouteImportEvent(
                    timestampMs = System.currentTimeMillis(),
                    success = false,
                    message = err.message ?: "El archivo compartido no es un GPX/KML/GeoJSON válido"
                )
            }
        )
        return res
    }

    fun consumeSharedImportEvent() {
        _sharedImportEvent.value = null
    }

    /**
     * Genera un archivo GPX 1.1 temporal listo para compartir con otras aplicaciones vía FileProvider.
     */
    fun exportRouteToShareableGpxFile(context: Context, route: SavedRouteEntry): File {
        val shareDir = File(context.cacheDir, "shared_gpx").apply { if (!exists()) mkdirs() }
        val safeName = route.title
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "ruta_mb_navigator" }
        val outFile = File(shareDir, "$safeName.gpx")

        val gpxBuilder = StringBuilder()
        gpxBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        gpxBuilder.append("<gpx version=\"1.1\" creator=\"MB-navigator\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        gpxBuilder.append("  <metadata><name>${escapeXml(route.title)}</name></metadata>\n")
        route.waypoints.forEach { wp ->
            gpxBuilder.append("  <wpt lat=\"${wp.point.latitude}\" lon=\"${wp.point.longitude}\">\n")
            gpxBuilder.append("    <name>${escapeXml(wp.name)}</name>\n")
            if (!wp.description.isNullOrBlank()) {
                gpxBuilder.append("    <desc>${escapeXml(wp.description)}</desc>\n")
            }
            gpxBuilder.append("  </wpt>\n")
        }
        gpxBuilder.append("  <trk>\n")
        gpxBuilder.append("    <name>${escapeXml(route.title)}</name>\n")
        gpxBuilder.append("    <trkseg>\n")
        route.points.forEach { pt ->
            gpxBuilder.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
            if (pt.elevationMeters != null) {
                gpxBuilder.append("<ele>${pt.elevationMeters}</ele>")
            }
            gpxBuilder.append("</trkpt>\n")
        }
        gpxBuilder.append("    </trkseg>\n")
        gpxBuilder.append("  </trk>\n")
        gpxBuilder.append("</gpx>\n")

        outFile.writeText(gpxBuilder.toString(), Charsets.UTF_8)
        return outFile
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIdx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Ruta_Importada.gpx"
    }

    private fun loadPersistedImportedRoutes(): List<SavedRouteEntry> {
        val dir = routesDir ?: return emptyList()
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val parsed = GpxKmlParser.parse(file.readText(), file.nameWithoutExtension)
                if (parsed.points.isEmpty()) return@mapNotNull null
                SavedRouteEntry(
                    id = file.nameWithoutExtension,
                    title = parsed.title,
                    distanceKm = parsed.distanceKm,
                    elevationGainM = parsed.elevationGainMeters,
                    curveCount = parsed.curveCount,
                    estimatedCurviness = "${parsed.curveCount} curvas",
                    format = parsed.format,
                    dateAdded = "Guardada",
                    points = parsed.points,
                    waypoints = parsed.waypoints
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Rutas predefinidas con coordenadas reales reales de carreteras de montaña españolas
     * para poder visualizar el track real sobre OpenStreetMap desde el primer instante.
     */
    private fun createBuiltInMotoRoutes(): List<SavedRouteEntry> {
        // 1. Puerto de Navacerrada -> Cotos -> Rascafría -> Puerto de La Morcuera -> Miraflores
        val guadarramaPoints = listOf(
            GeoPoint(40.7279, -4.0038, 1200.0), // Navacerrada pueblo
            GeoPoint(40.7425, -4.0085, 1340.0), // M-601 subida
            GeoPoint(40.7582, -4.0156, 1510.0), // Curvas Ventorrillo
            GeoPoint(40.7745, -4.0112, 1690.0), // Siete Picos
            GeoPoint(40.7885, -4.0032, 1858.0), // Alto Puerto de Navacerrada
            GeoPoint(40.8034, -3.9875, 1840.0), // M-604 hacia Cotos
            GeoPoint(40.8226, -3.9628, 1830.0), // Puerto de Cotos
            GeoPoint(40.8481, -3.9412, 1520.0), // Bajada paellas Rascafría
            GeoPoint(40.8712, -3.9120, 1310.0),
            GeoPoint(40.8965, -3.8825, 1175.0),
            GeoPoint(40.9045, -3.8794, 1160.0), // Rascafría
            GeoPoint(40.8762, -3.8548, 1360.0), // M-611 hacia La Morcuera
            GeoPoint(40.8510, -3.8410, 1590.0),
            GeoPoint(40.8295, -3.8318, 1796.0), // Puerto de la Morcuera
            GeoPoint(40.8164, -3.8025, 1540.0), // Bajada Miraflores
            GeoPoint(40.8124, -3.7675, 1147.0)  // Miraflores de la Sierra
        )

        // 2. Desfiladero de La Hermida -> Potes -> Fuente Dé (Picos de Europa)
        val picosPoints = listOf(
            GeoPoint(43.3210, -4.5880, 55.0),  // Panes (N-621)
            GeoPoint(43.2965, -4.5992, 110.0), // Entrada Desfiladero La Hermida
            GeoPoint(43.2712, -4.6095, 145.0),
            GeoPoint(43.2535, -4.6118, 180.0), // La Hermida
            GeoPoint(43.2315, -4.6085, 220.0),
            GeoPoint(43.2045, -4.5912, 265.0), // Lebeña
            GeoPoint(43.1758, -4.6112, 285.0), // Tama
            GeoPoint(43.1534, -4.6238, 291.0), // Potes
            GeoPoint(43.1492, -4.6615, 420.0), // CA-185 hacia Fuente Dé
            GeoPoint(43.1435, -4.7120, 610.0), // Camaleño
            GeoPoint(43.1412, -4.7645, 830.0), // Espinama
            GeoPoint(43.1438, -4.8125, 1078.0) // Fuente Dé
        )

        // 3. Serranía de Cuenca / Ruta de los Pantanos (Sacedón - Entrepeñas - Priego)
        val pantanosPoints = listOf(
            GeoPoint(40.4832, -2.7315, 740.0), // Sacedón (N-320)
            GeoPoint(40.5090, -2.7125, 710.0), // Presa de Entrepeñas
            GeoPoint(40.5425, -2.6680, 765.0), // Cifuentes / Durón N-204
            GeoPoint(40.5562, -2.5912, 810.0),
            GeoPoint(40.5025, -2.5015, 850.0), // Trillo
            GeoPoint(40.4480, -2.4112, 890.0),
            GeoPoint(40.4385, -2.3130, 865.0), // Priego (Hoz de Priego)
            GeoPoint(40.3910, -2.2740, 940.0)
        )

        return listOf(
            SavedRouteEntry(
                id = "builtin-guadarrama",
                title = "Sierra de Guadarrama: Navacerrada y Morcuera",
                distanceKm = HaversineDistanceCalculator.totalDistanceMeters(guadarramaPoints) / 1000.0,
                elevationGainM = 1650.0,
                curveCount = GpxKmlParser.countSignificantCurves(guadarramaPoints),
                estimatedCurviness = "Muy Alta (Puerto clásico)",
                format = "GPX 1.1",
                dateAdded = "Incluida",
                points = guadarramaPoints,
                waypoints = listOf(
                    Waypoint(name = "Puerto Navacerrada", point = GeoPoint(40.7885, -4.0032)),
                    Waypoint(name = "Puerto de Cotos", point = GeoPoint(40.8226, -3.9628)),
                    Waypoint(name = "Puerto de La Morcuera", point = GeoPoint(40.8295, -3.8318))
                )
            ),
            SavedRouteEntry(
                id = "builtin-picos",
                title = "Picos de Europa: La Hermida y Fuente Dé",
                distanceKm = HaversineDistanceCalculator.totalDistanceMeters(picosPoints) / 1000.0,
                elevationGainM = 1320.0,
                curveCount = GpxKmlParser.countSignificantCurves(picosPoints),
                estimatedCurviness = "Extrema (Cañón y alta montaña)",
                format = "GPX 1.1",
                dateAdded = "Incluida",
                points = picosPoints,
                waypoints = listOf(
                    Waypoint(name = "Potes", point = GeoPoint(43.1534, -4.6238)),
                    Waypoint(name = "Fuente Dé", point = GeoPoint(43.1438, -4.8125))
                )
            ),
            SavedRouteEntry(
                id = "builtin-cuenca",
                title = "Ruta de los Pantanos y Hoz de Priego",
                distanceKm = HaversineDistanceCalculator.totalDistanceMeters(pantanosPoints) / 1000.0,
                elevationGainM = 840.0,
                curveCount = GpxKmlParser.countSignificantCurves(pantanosPoints),
                estimatedCurviness = "Alta (Curvas rápidas)",
                format = "GPX 1.1",
                dateAdded = "Incluida",
                points = pantanosPoints
            )
        )
    }
}
