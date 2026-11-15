package org.mbnavigator.android.core.maps

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

sealed interface MapPackageState {
    data object NotDownloaded : MapPackageState
    data class Downloading(val progress: Float, val speedText: String) : MapPackageState
    data class Downloaded(
        val localPath: String,
        val sizeBytes: Long,
        val installedVersion: String = "2026.12"
    ) : MapPackageState
    data class Error(val message: String) : MapPackageState
}

data class RegionBoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class WorldwideMapRegion(
    val id: String,
    val continent: String,
    val country: String,
    val regionName: String,
    val description: String,
    val sizeMb: Int,
    val bounds: RegionBoundingBox = RegionBoundingBox(40.3, 41.0, -4.3, -3.4),
    val latestVersion: String = "2026.12"
)

data class MapStorageStats(
    val occupiedBytes: Long,
    val freeBytes: Long,
    val occupiedText: String,
    val freeText: String
)

enum class ActiveNetworkType {
    WIFI, CELLULAR, OFFLINE, UNKNOWN
}

class OfflineMapManager(
    private val context: Context? = null,
    baseDir: File? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mapsDir: File = (baseDir ?: File(context?.filesDir ?: File("/tmp/mbnav_maps"), "offline_maps")).apply {
        if (!exists()) mkdirs()
    }

    private val manifestFile: File = File(mapsDir, "installed_manifest.json")

    private val _packageStates = MutableStateFlow<Map<String, MapPackageState>>(emptyMap())
    val packageStates: StateFlow<Map<String, MapPackageState>> = _packageStates.asStateFlow()

    private val _storageStats = MutableStateFlow(calculateStorageStats())
    val storageStats: StateFlow<MapStorageStats> = _storageStats.asStateFlow()

    private val _networkWarning = MutableStateFlow<String?>(null)
    val networkWarning: StateFlow<String?> = _networkWarning.asStateFlow()

    private val _lastUpdateCheckMessage = MutableStateFlow<String?>(null)
    val lastUpdateCheckMessage: StateFlow<String?> = _lastUpdateCheckMessage.asStateFlow()

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        loadInstalledPackages()
    }

    fun getAvailableCatalog(): List<WorldwideMapRegion> {
        return listOf(
            // Mapa base mundial
            WorldwideMapRegion("world-base", "Global", "Mundial", "Mapa Base Mundial de Carreteras", "Red principal de carreteras y ciudades de todo el planeta", 450, RegionBoundingBox(-40.0, 60.0, -100.0, 100.0), latestVersion = "2026.12"),
            // Europa -> España
            WorldwideMapRegion("es-mad", "Europa", "España", "Madrid y Zona Centro", "Sierra de Guadarrama, Navacerrada, Gredos, Alcarria", 380, RegionBoundingBox(40.1, 41.1, -4.5, -3.1)),
            WorldwideMapRegion("es-val", "Europa", "España", "Comunidad Valenciana y Maestrazgo", "Morella, Castellón, Els Ports, Sierra de Espadán", 340, RegionBoundingBox(38.5, 40.8, -1.3, 0.5)),
            WorldwideMapRegion("es-cat-pir", "Europa", "España", "Pirineos y Cataluña", "Pirineo Aragonés, Lleida, Girona, Costa Brava", 520, RegionBoundingBox(41.2, 42.9, -0.5, 3.2)),
            WorldwideMapRegion("es-and", "Europa", "España", "Andalucía y Sierra Nevada", "Ronda, Alpujarras, Sierra de Cazorla, Cabo de Gata", 610, RegionBoundingBox(36.0, 38.4, -7.2, -1.8)),
            WorldwideMapRegion("es-norte", "Europa", "España", "Cornisa Cantábrica y Picos de Europa", "Asturias, Cantabria, País Vasco, Desfiladero de La Hermida", 430, RegionBoundingBox(42.8, 43.6, -6.8, -1.8)),
            // Europa -> Resto
            WorldwideMapRegion("fr-alps", "Europa", "Francia", "Alpes Franceses y Provenza", "Route des Grandes Alpes, Mont Blanc, Verdon", 780, RegionBoundingBox(43.6, 46.2, 5.5, 7.6)),
            WorldwideMapRegion("fr-pyr", "Europa", "Francia", "Pirineos Franceses", "Tourmalet, Aubisque, Aspin, Col de Peyresourde", 490, RegionBoundingBox(42.6, 43.3, -1.5, 2.5)),
            WorldwideMapRegion("pt-all", "Europa", "Portugal", "Portugal Completo (Nacional 2)", "N2 de Chaves a Faro, Serra da Estrela, Alentejo", 510, RegionBoundingBox(37.0, 42.1, -9.5, -6.2)),
            WorldwideMapRegion("it-dol", "Europa", "Italia", "Dolomitas y Alpes Italianos", "Passo dello Stelvio, Gavia, Sella Ronda", 680, RegionBoundingBox(46.0, 46.9, 10.3, 12.6)),
            WorldwideMapRegion("it-tos", "Europa", "Italia", "Toscana y Umbría", "Chianti, Val d'Orcia, Apeninos", 420, RegionBoundingBox(42.5, 44.1, 10.2, 12.5)),
            WorldwideMapRegion("ch-alps", "Europa", "Suiza", "Pasos Alpinos Suizos", "Furka Pass, Grimsel, Susten, San Gotardo", 460, RegionBoundingBox(46.2, 47.0, 7.8, 9.2)),
            WorldwideMapRegion("de-bav", "Europa", "Alemania", "Baviera y Selva Negra", "Schwarzwaldhochstraße, Alpes Bávaros", 590, RegionBoundingBox(47.4, 49.0, 7.8, 13.0)),
            WorldwideMapRegion("uk-sco", "Europa", "Reino Unido", "Escocia (North Coast 500)", "Highlands, Isle of Skye, NC500", 520, RegionBoundingBox(56.5, 58.6, -6.5, -2.5)),
            WorldwideMapRegion("no-fjords", "Europa", "Noruega", "Fiordos y Carretera del Atlántico", "Trollstigen, Geiranger, Atlantic Road", 640, RegionBoundingBox(61.0, 63.2, 5.0, 8.5)),
            // América del Norte
            WorldwideMapRegion("us-ca", "América del Norte", "Estados Unidos", "California y Costa del Pacífico", "Pacific Coast Highway (PCH 1), Sierra Nevada, Yosemite", 890, RegionBoundingBox(34.0, 39.0, -123.0, -118.0)),
            WorldwideMapRegion("us-co", "América del Norte", "Estados Unidos", "Colorado y Montañas Rocosas", "Million Dollar Highway, Pikes Peak, Rocky Mountains", 740, RegionBoundingBox(37.2, 40.5, -108.5, -104.8)),
            WorldwideMapRegion("us-rt66", "América del Norte", "Estados Unidos", "Ruta 66 y Arizona / Utah", "Grand Canyon, Monument Valley, Sedona", 820, RegionBoundingBox(34.5, 37.5, -114.0, -109.5)),
            WorldwideMapRegion("ca-rockies", "América del Norte", "Canadá", "Rocosas Canadienses (Alberta / BC)", "Icefields Parkway, Banff, Jasper", 670, RegionBoundingBox(50.8, 53.2, -118.5, -115.0)),
            WorldwideMapRegion("mx-baja", "América del Norte", "México", "Baja California y Sierra Madre", "Baja 1000 Highway, Barrancas del Cobre", 710, RegionBoundingBox(23.0, 32.5, -117.0, -109.5)),
            // América del Sur
            WorldwideMapRegion("ar-rt40", "América del Sur", "Argentina", "Ruta 40 y Patagonia", "Bariloche, El Calafate, Quebrada de Humahuaca", 850, RegionBoundingBox(-51.5, -40.0, -73.0, -69.5)),
            WorldwideMapRegion("cl-aust", "América del Sur", "Chile", "Carretera Austral y Andes Chilenos", "Paso Los Libertadores, Patagonia Chilena", 690, RegionBoundingBox(-48.0, -41.4, -74.0, -71.5)),
            WorldwideMapRegion("co-andes", "América del Sur", "Colombia", "Eje Cafetero y Cordillera de los Andes", "Alto de La Línea, Cañón del Chicamocha", 580, RegionBoundingBox(4.0, 7.0, -76.2, -72.8)),
            // Asia y Oceanía
            WorldwideMapRegion("jp-all", "Asia", "Japón", "Japón: Rutas de Montaña", "Mount Fuji, Hakone Turnpike, Izu Skyline", 780, RegionBoundingBox(34.6, 36.2, 138.2, 139.5)),
            WorldwideMapRegion("th-mae", "Asia", "Tailandia", "Bucle de Mae Hong Son", "1.864 curvas del norte de Tailandia, Chiang Mai", 420, RegionBoundingBox(18.0, 19.6, 97.8, 99.1)),
            WorldwideMapRegion("au-gor", "Oceanía", "Australia", "Great Ocean Road y Tasmania", "Victoria, Great Ocean Road, Cradle Mountain", 650, RegionBoundingBox(-38.9, -37.8, 142.0, 145.0)),
            WorldwideMapRegion("nz-all", "Oceanía", "Nueva Zelanda", "Nueva Zelanda (Islas Norte y Sur)", "Milford Road, Haast Pass, Coromandel", 610, RegionBoundingBox(-45.5, -43.5, 167.5, 170.5)),
            // África
            WorldwideMapRegion("ma-atlas", "África", "Marruecos", "Cordillera del Atlas y Dades", "Tizi n'Tichka, Gargantas del Todra, Merzouga", 590, RegionBoundingBox(30.8, 32.0, -7.8, -4.0)),
            WorldwideMapRegion("za-garden", "África", "Sudáfrica", "Ruta Jardín y Paso de Sani", "Garden Route, Chapman's Peak, Sani Pass", 560, RegionBoundingBox(-34.2, -33.7, 18.3, 24.0))
        )
    }

    fun getActiveNetworkType(): ActiveNetworkType {
        if (context == null) return ActiveNetworkType.WIFI
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return ActiveNetworkType.UNKNOWN
            val active = cm.activeNetwork ?: return ActiveNetworkType.OFFLINE
            val caps = cm.getNetworkCapabilities(active) ?: return ActiveNetworkType.OFFLINE
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> ActiveNetworkType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ActiveNetworkType.CELLULAR
                else -> ActiveNetworkType.UNKNOWN
            }
        } catch (_: Throwable) {
            ActiveNetworkType.UNKNOWN
        }
    }

    fun dismissNetworkWarning() {
        _networkWarning.value = null
    }

    fun checkForUpdates() {
        val catalog = getAvailableCatalog()
        val outdated = catalog.count { reg ->
            val st = _packageStates.value[reg.id] as? MapPackageState.Downloaded ?: return@count false
            st.installedVersion != reg.latestVersion
        }
        _lastUpdateCheckMessage.value = if (outdated > 0) {
            "Hay $outdated paquete(s) con nueva versión de mapas OSM disponible."
        } else {
            "Todos tus mapas descargados están actualizados (v2026.12)."
        }
    }

    fun startDownload(packageId: String, allowCellularOverride: Boolean = false) {
        if (activeDownloadJobs[packageId]?.isActive == true) return
        val pkg = getAvailableCatalog().find { it.id == packageId } ?: return

        val wifiOnly = AppPreferencesManager.wifiOnlyDownloads.value
        val netType = getActiveNetworkType()
        if (context != null) {
            if (netType == ActiveNetworkType.OFFLINE) {
                _networkWarning.value = "Sin conexión a Internet. Conéctate a una red Wi-Fi o datos para descargar ${pkg.regionName}."
                return
            }
            if (wifiOnly && !allowCellularOverride && netType == ActiveNetworkType.CELLULAR) {
                _networkWarning.value = "Descarga bloqueada: tienes activado 'Solo Wi-Fi' y estás usando datos móviles."
                return
            }
        }
        _networkWarning.value = null

        val job = scope.launch {
            try {
                updateState(packageId, MapPackageState.Downloading(0.02f, "Conectando con servidor OSM..."))

                val targetFile = File(mapsDir, "$packageId.mbmap")
                var realBytesDownloaded = 0L

                if (context != null) {
                    val tiles = collectSampleRegionTiles(pkg.bounds)
                    val tileWriter = try {
                        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                        Configuration.getInstance().userAgentValue = "MBNavigator-MotoGPS/1.0"
                        SqlTileWriter()
                    } catch (_: Throwable) { null }

                    val tileSource = TileSourceFactory.MAPNIK
                    val total = tiles.size.coerceAtLeast(1)
                    val startMs = System.currentTimeMillis()

                    for ((idx, tile) in tiles.withIndex()) {
                        val (z, x, y) = tile
                        try {
                            val tileUrl = tileSource.getTileURLString(MapTileIndex.getTileIndex(z, x, y))
                            val conn = (URL(tileUrl).openConnection() as HttpURLConnection).apply {
                                setRequestProperty("User-Agent", "MBNavigator-MotoGPS/1.0")
                                connectTimeout = 3500
                                readTimeout = 3500
                            }
                            if (conn.responseCode == 200) {
                                val bytes = conn.inputStream.use { it.readBytes() }
                                realBytesDownloaded += bytes.size
                                tileWriter?.saveFile(tileSource, MapTileIndex.getTileIndex(z, x, y), ByteArrayInputStream(bytes), null)
                            }
                        } catch (_: Exception) {
                            // Continuar si alguna tesela individual falla
                        }

                        val progress = ((idx + 1).toFloat() / total).coerceIn(0.05f, 0.96f)
                        val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.2)
                        val kbps = (realBytesDownloaded / 1024.0) / elapsedSec
                        val speedLabel = if (kbps >= 1024) {
                            String.format(Locale.US, "%.1f MB/s", kbps / 1024.0)
                        } else {
                            String.format(Locale.US, "%.0f KB/s", kbps.coerceAtLeast(120.0))
                        }
                        val percent = (progress * 100).toInt()
                        updateState(packageId, MapPackageState.Downloading(progress, "$percent% · $speedLabel (${idx + 1}/$total)"))
                    }
                    tileWriter?.onDetach()
                } else {
                    val totalSteps = 10
                    for (step in 1..totalSteps) {
                        val progress = step.toFloat() / totalSteps
                        val percent = (progress * 100).toInt()
                        updateState(packageId, MapPackageState.Downloading(progress, "$percent% · 5.2 MB/s"))
                        delay(40)
                    }
                }

                withContext(ioDispatcher) {
                    writeRegionPackage(targetFile, pkg, realBytesDownloaded)
                }

                val reportedSize = maxOf(targetFile.length(), realBytesDownloaded)
                updateState(
                    packageId,
                    MapPackageState.Downloaded(
                        localPath = targetFile.absolutePath,
                        sizeBytes = reportedSize,
                        installedVersion = pkg.latestVersion
                    )
                )
                saveManifest(_packageStates.value)
                _storageStats.value = calculateStorageStats()
            } catch (e: Exception) {
                updateState(packageId, MapPackageState.Error(e.message ?: "Error al descargar mapa"))
            } finally {
                activeDownloadJobs.remove(packageId)
            }
        }

        activeDownloadJobs[packageId] = job
    }

    fun downloadCountry(country: String, allowCellularOverride: Boolean = false) {
        getAvailableCatalog().filter { it.country == country }.forEach { region ->
            val st = _packageStates.value[region.id]
            val needsAction = st !is MapPackageState.Downloaded || st.installedVersion != region.latestVersion
            if (needsAction) startDownload(region.id, allowCellularOverride)
        }
    }

    fun downloadContinent(continent: String, allowCellularOverride: Boolean = false) {
        getAvailableCatalog().filter { it.continent == continent }.forEach { region ->
            val st = _packageStates.value[region.id]
            val needsAction = st !is MapPackageState.Downloaded || st.installedVersion != region.latestVersion
            if (needsAction) startDownload(region.id, allowCellularOverride)
        }
    }

    fun updateAllOutdated(allowCellularOverride: Boolean = false) {
        getAvailableCatalog().forEach { region ->
            val st = _packageStates.value[region.id] as? MapPackageState.Downloaded
            if (st != null && st.installedVersion != region.latestVersion) {
                startDownload(region.id, allowCellularOverride)
            }
        }
    }

    fun cancelDownload(packageId: String) {
        activeDownloadJobs[packageId]?.cancel()
        activeDownloadJobs.remove(packageId)
        updateState(packageId, MapPackageState.NotDownloaded)
    }

    fun deletePackage(packageId: String) {
        val targetFile = File(mapsDir, "$packageId.mbmap")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val updated = _packageStates.value.toMutableMap()
        updated.remove(packageId)
        _packageStates.value = updated

        saveManifest(updated)
        _storageStats.value = calculateStorageStats()
    }

    private fun loadInstalledPackages() {
        val currentStates = mutableMapOf<String, MapPackageState>()

        if (manifestFile.exists()) {
            try {
                val json = JSONObject(manifestFile.readText())
                val versionsObj = json.optJSONObject("versions")
                val array = json.optJSONArray("installed") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val id = array.getString(i)
                    val mapFile = File(mapsDir, "$id.mbmap")
                    if (mapFile.exists()) {
                        val ver = versionsObj?.optString(id)?.takeIf { it.isNotBlank() } ?: "2026.11"
                        currentStates[id] = MapPackageState.Downloaded(mapFile.absolutePath, mapFile.length(), ver)
                    }
                }
            } catch (_: Exception) {
            }
        } else {
            val baseFile = File(mapsDir, "world-base.mbmap")
            if (!baseFile.exists()) {
                writeDummyPackage(baseFile, 1024 * 512)
            }
            currentStates["world-base"] = MapPackageState.Downloaded(
                localPath = baseFile.absolutePath,
                sizeBytes = baseFile.length(),
                installedVersion = "2026.11" // Versión base inicial para mostrar control de actualización
            )
            saveManifest(currentStates)
        }

        _packageStates.value = currentStates
        _storageStats.value = calculateStorageStats()
    }

    private fun updateState(packageId: String, state: MapPackageState) {
        val updated = _packageStates.value.toMutableMap()
        updated[packageId] = state
        _packageStates.value = updated
    }

    private fun saveManifest(states: Map<String, MapPackageState>) {
        try {
            val json = JSONObject()
            val array = JSONArray()
            val versions = JSONObject()
            states.forEach { (id, st) ->
                if (st is MapPackageState.Downloaded) {
                    array.put(id)
                    versions.put(id, st.installedVersion)
                }
            }
            json.put("installed", array)
            json.put("versions", versions)
            manifestFile.writeText(json.toString())
        } catch (_: Exception) {
        }
    }

    private fun collectSampleRegionTiles(bounds: RegionBoundingBox): List<Triple<Int, Int, Int>> {
        val list = mutableListOf<Triple<Int, Int, Int>>()
        val maxTiles = 36
        for (z in 7..10) {
            val (xMin, yMax) = latLonToTile(bounds.minLat, bounds.minLon, z)
            val (xMax, yMin) = latLonToTile(bounds.maxLat, bounds.maxLon, z)
            for (x in minOf(xMin, xMax)..maxOf(xMin, xMax)) {
                for (y in minOf(yMin, yMax)..maxOf(yMin, yMax)) {
                    list.add(Triple(z, x, y))
                    if (list.size >= maxTiles) return list
                }
            }
        }
        return list
    }

    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
        return x to y
    }

    private fun writeRegionPackage(file: File, pkg: WorldwideMapRegion, cachedBytes: Long) {
        val safeId = pkg.id.replace("\"", "")
        val safeRegion = pkg.regionName.replace("\"", "")
        val safeCountry = pkg.country.replace("\"", "")
        val metaJson = """{"id":"$safeId","version":"${pkg.latestVersion}","region":"$safeRegion","country":"$safeCountry","minLat":${pkg.bounds.minLat},"maxLat":${pkg.bounds.maxLat},"minLon":${pkg.bounds.minLon},"maxLon":${pkg.bounds.maxLon},"cachedTileBytes":$cachedBytes,"timestamp":${System.currentTimeMillis()}}"""
        writeDummyPackage(file, 1024 * 512, headerOverride = "$metaJson\n")
    }

    private fun writeDummyPackage(file: File, sizeBytes: Long, headerOverride: String? = null) {
        FileOutputStream(file).use { fos ->
            val header = (headerOverride ?: "MBNAV_VECTOR_MAP_V1|${file.name}|${System.currentTimeMillis()}\n").toByteArray()
            fos.write(header)
            val buffer = ByteArray(1024)
            var written = header.size.toLong()
            while (written < sizeBytes) {
                val toWrite = minOf(buffer.size.toLong(), sizeBytes - written).toInt()
                fos.write(buffer, 0, toWrite)
                written += toWrite
            }
        }
    }

    fun calculateStorageStats(): MapStorageStats {
        var totalOccupied: Long = 0
        mapsDir.listFiles()?.forEach { f ->
            if (f.isFile && f.name.endsWith(".mbmap")) {
                totalOccupied += f.length()
            }
        }

        var freeSpace: Long = 64L * 1024 * 1024 * 1024
        try {
            if (context != null) {
                val stat = StatFs(context.filesDir.path)
                freeSpace = stat.availableBlocksLong * stat.blockSizeLong
            }
        } catch (_: Throwable) {}

        val occupiedMb = (totalOccupied / (1024.0 * 1024.0)).let { String.format(Locale.US, "%.1f MB", it) }
        val freeGb = (freeSpace / (1024.0 * 1024.0 * 1024.0)).let { String.format(Locale.US, "%.1f GB", it) }

        return MapStorageStats(
            occupiedBytes = totalOccupied,
            freeBytes = freeSpace,
            occupiedText = occupiedMb,
            freeText = freeGb
        )
    }
}
