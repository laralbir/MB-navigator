package org.mbnavigator.android.core.maps

import android.content.Context
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
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale

sealed interface MapPackageState {
    data object NotDownloaded : MapPackageState
    data class Downloading(val progress: Float, val speedText: String) : MapPackageState
    data class Downloaded(val localPath: String, val sizeBytes: Long) : MapPackageState
    data class Error(val message: String) : MapPackageState
}

data class WorldwideMapRegion(
    val id: String,
    val continent: String,
    val country: String,
    val regionName: String,
    val description: String,
    val sizeMb: Int,
    val downloadUrl: String = ""
)

data class MapStorageStats(
    val occupiedBytes: Long,
    val freeBytes: Long,
    val occupiedText: String,
    val freeText: String
)

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

    private val activeDownloadJobs = mutableMapOf<String, Job>()

    init {
        loadInstalledPackages()
    }

    fun getAvailableCatalog(): List<WorldwideMapRegion> {
        return listOf(
            // Mapa base mundial
            WorldwideMapRegion("world-base", "Global", "Mundial", "🗺️ Mapa Base Mundial de Carreteras", "Red principal de carreteras y ciudades de todo el planeta", 450),
            // Europa
            WorldwideMapRegion("es-mad", "Europa", "España", "Madrid y Zona Centro", "Guadarrama, Gredos, Alcarria", 380),
            WorldwideMapRegion("es-cat-pir", "Europa", "España", "Pirineos y Cataluña", "Pirineo Aragonés, Lleida, Girona, Costa Brava", 520),
            WorldwideMapRegion("es-and", "Europa", "España", "Andalucía y Sierra Nevada", "Ronda, Alpujarras, Sierra de Cazorla, Cabo de Gata", 610),
            WorldwideMapRegion("es-norte", "Europa", "España", "Cornisa Cantábrica y Picos de Europa", "Asturias, Cantabria, País Vasco", 430),
            WorldwideMapRegion("fr-alps", "Europa", "Francia", "Alpes Franceses y Provenza", "Route des Grandes Alpes, Mont Blanc, Verdon", 780),
            WorldwideMapRegion("fr-pyr", "Europa", "Francia", "Pirineos Franceses", "Tourmalet, Aubisque, Aspin, Col de Peyresourde", 490),
            WorldwideMapRegion("pt-all", "Europa", "Portugal", "Portugal Completo (Nacional 2)", "N2 de Chaves a Faro, Serra da Estrela", 510),
            WorldwideMapRegion("it-dol", "Europa", "Italia", "Dolomitas y Alpes Italianos", "Passo dello Stelvio, Gavia, Sella Ronda", 680),
            WorldwideMapRegion("ch-alps", "Europa", "Suiza", "Pasos Alpinos Suizos", "Furka Pass, Grimsel, Susten, San Gotardo", 460),
            WorldwideMapRegion("de-bav", "Europa", "Alemania", "Baviera y Selva Negra", "Schwarzwaldhochstraße, Alpes Bávaros", 590),
            WorldwideMapRegion("uk-sco", "Europa", "Reino Unido", "Escocia (North Coast 500)", "Highlands, Isle of Skye, NC500", 520),
            WorldwideMapRegion("no-fjords", "Europa", "Noruega", "Fiordos y Carretera del Atlántico", "Trollstigen, Geiranger, Atlantic Road", 640),
            // América del Norte
            WorldwideMapRegion("us-ca", "América del Norte", "Estados Unidos", "California y Costa del Pacífico", "Pacific Coast Highway (PCH 1), Sierra Nevada, Yosemite", 890),
            WorldwideMapRegion("us-co", "América del Norte", "Estados Unidos", "Colorado y Montañas Rocosas", "Million Dollar Highway, Pikes Peak, Rocky Mountains", 740),
            WorldwideMapRegion("us-rt66", "América del Norte", "Estados Unidos", "Ruta 66 y Arizona / Utah", "Grand Canyon, Monument Valley, Sedona", 820),
            WorldwideMapRegion("ca-rockies", "América del Norte", "Canadá", "Rocosas Canadienses (Alberta / BC)", "Icefields Parkway, Banff, Jasper", 670),
            WorldwideMapRegion("mx-baja", "América del Norte", "México", "Baja California y Sierra Madre", "Baja 1000 Highway, Barrancas del Cobre", 710),
            // América del Sur
            WorldwideMapRegion("ar-rt40", "América del Sur", "Argentina", "Ruta 40 y Patagonia", "Bariloche, El Calafate, Quebrada de Humahuaca", 850),
            WorldwideMapRegion("cl-aust", "América del Sur", "Chile", "Carretera Austral y Andes Chilenos", "Paso Los Libertadores, Patagonia Chilena", 690),
            WorldwideMapRegion("co-andes", "América del Sur", "Colombia", "Eje Cafetero y Cordillera de los Andes", "Alto de La Línea, Cañón del Chicamocha", 580),
            // Asia y Oceanía
            WorldwideMapRegion("jp-all", "Asia", "Japón", "Japón: Rutas de Montaña", "Mount Fuji, Hakone Turnpike, Izu Skyline", 780),
            WorldwideMapRegion("th-mae", "Asia", "Tailandia", "Bucle de Mae Hong Son", "1.864 curvas del norte de Tailandia, Chiang Mai", 420),
            WorldwideMapRegion("au-gor", "Oceanía", "Australia", "Great Ocean Road y Tasmania", "Victoria, Great Ocean Road, Cradle Mountain", 650),
            WorldwideMapRegion("nz-all", "Oceanía", "Nueva Zelanda", "Nueva Zelanda (Islas Norte y Sur)", "Milford Road, Haast Pass, Coromandel", 610),
            // África
            WorldwideMapRegion("ma-atlas", "África", "Marruecos", "Cordillera del Atlas y Gargantas del Dades", "Tizi n'Tichka, Gargantas del Todra, Merzouga", 590),
            WorldwideMapRegion("za-garden", "África", "Sudáfrica", "Ruta Jardín y Paso de Sani", "Garden Route, Chapman's Peak, Sani Pass", 560)
        )
    }

    private fun loadInstalledPackages() {
        val currentStates = mutableMapOf<String, MapPackageState>()

        // Pre-cargar mapa base como disponible por defecto si existe o crear manifest
        if (manifestFile.exists()) {
            try {
                val json = JSONObject(manifestFile.readText())
                val array = json.optJSONArray("installed") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val id = array.getString(i)
                    val mapFile = File(mapsDir, "$id.mbmap")
                    if (mapFile.exists()) {
                        currentStates[id] = MapPackageState.Downloaded(mapFile.absolutePath, mapFile.length())
                    }
                }
            } catch (_: Exception) {
                // Ignore parse errors
            }
        } else {
            // Inicializar paquete base inicial si no existe
            val baseFile = File(mapsDir, "world-base.mbmap")
            if (!baseFile.exists()) {
                writeDummyPackage(baseFile, 1024 * 512) // 512 KB base inicial
            }
            currentStates["world-base"] = MapPackageState.Downloaded(baseFile.absolutePath, baseFile.length())
            saveManifest(setOf("world-base"))
        }

        _packageStates.value = currentStates
        _storageStats.value = calculateStorageStats()
    }

    fun startDownload(packageId: String) {
        if (activeDownloadJobs[packageId]?.isActive == true) return

        val pkg = getAvailableCatalog().find { it.id == packageId } ?: return

        val job = scope.launch {
            try {
                // Progreso inicial
                updateState(packageId, MapPackageState.Downloading(0.02f, "Conectando al servidor..."))
                delay(300)

                val targetFile = File(mapsDir, "$packageId.mbmap")
                val totalSteps = 20
                for (step in 1..totalSteps) {
                    val progress = step.toFloat() / totalSteps
                    val mbps = (4.0 + (step % 5) * 1.2).let { String.format(Locale.US, "%.1f MB/s", it) }
                    val percent = (progress * 100).toInt()
                    updateState(packageId, MapPackageState.Downloading(progress, "$percent% • $mbps"))
                    delay(150)
                }

                // Escribir archivo real en disco con metadata
                withContext(ioDispatcher) {
                    // Escribir archivo simulando el tamaño proporcional (ej. 1MB real para no saturar memoria)
                    writeDummyPackage(targetFile, 1024 * 1024)
                }

                // Guardar en manifest
                val currentInstalled = _packageStates.value.keys.toMutableSet()
                currentInstalled.add(packageId)
                saveManifest(currentInstalled)

                // Actualizar estado final
                updateState(packageId, MapPackageState.Downloaded(targetFile.absolutePath, targetFile.length()))
                _storageStats.value = calculateStorageStats()
            } catch (e: Exception) {
                updateState(packageId, MapPackageState.Error(e.message ?: "Error al descargar mapa"))
            } finally {
                activeDownloadJobs.remove(packageId)
            }
        }

        activeDownloadJobs[packageId] = job
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

        saveManifest(updated.keys)
        _storageStats.value = calculateStorageStats()
    }

    private fun updateState(packageId: String, state: MapPackageState) {
        val updated = _packageStates.value.toMutableMap()
        updated[packageId] = state
        _packageStates.value = updated
    }

    private fun saveManifest(installedIds: Set<String>) {
        try {
            val json = JSONObject()
            val array = JSONArray()
            installedIds.forEach { array.put(it) }
            json.put("installed", array)
            manifestFile.writeText(json.toString())
        } catch (_: Exception) {}
    }

    private fun writeDummyPackage(file: File, sizeBytes: Long) {
        FileOutputStream(file).use { fos ->
            val header = "MBNAV_VECTOR_MAP_V1|${file.name}|${System.currentTimeMillis()}\n".toByteArray()
            fos.write(header)
            // Llenar con buffer hasta tamaño mínimo
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

        var freeSpace: Long = 64L * 1024 * 1024 * 1024 // 64 GB fallback
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
