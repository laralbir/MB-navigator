package org.mbnavigator.android.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mbnavigator.android.core.maps.MapPackageState
import org.mbnavigator.android.core.maps.OfflineMapManager
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapManagerTest {

    private lateinit var tempDir: File
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        tempDir = File.createTempFile("mbnav_test_maps", "").apply {
            delete()
            mkdirs()
        }
    }

    @Test
    fun test_catalog_contains_worldwide_regions_and_base_map() {
        val manager = OfflineMapManager(baseDir = tempDir, scope = testScope, ioDispatcher = testDispatcher)
        val catalog = manager.getAvailableCatalog()

        assertTrue("El catálogo debe contener regiones mundiales", catalog.size >= 20)
        assertNotNull("Debe existir el mapa base mundial", catalog.find { it.id == "world-base" })
        assertNotNull("Debe existir mapa de España / Pirineos", catalog.find { it.id == "es-cat-pir" })
        assertNotNull("Debe existir mapa de Dolomitas", catalog.find { it.id == "it-dol" })
        assertNotNull("Debe existir mapa de Ruta 40 Argentina", catalog.find { it.id == "ar-rt40" })
    }

    @Test
    fun test_offline_map_manager_agent_downloads_and_persists_packages_to_local_storage() = testScope.runTest {
        val manager = OfflineMapManager(baseDir = tempDir, scope = testScope, ioDispatcher = testDispatcher)

        val targetPackageId = "es-cat-pir"
        manager.startDownload(targetPackageId)

        // Avanzar el tiempo virtual del dispatcher de pruebas
        advanceUntilIdle()

        val state = manager.packageStates.value[targetPackageId]
        assertTrue("El mapa debe quedar descargado en almacenamiento local", state is MapPackageState.Downloaded)

        val downloaded = state as MapPackageState.Downloaded
        val mapFile = File(downloaded.localPath)
        assertTrue("El archivo debe existir físicamente en disco", mapFile.exists())
        assertTrue("El archivo debe tener bytes reales en disco", mapFile.length() > 0)

        // Verificar cálculo de almacenamiento
        val stats = manager.calculateStorageStats()
        assertTrue("Los bytes ocupados deben ser mayores a 0", stats.occupiedBytes > 0)

        // Eliminar mapa y verificar desinstalación
        manager.deletePackage(targetPackageId)
        val stateAfterDelete = manager.packageStates.value[targetPackageId]
        assertEquals("El estado tras borrar debe ser null o NotDownloaded", null, stateAfterDelete)
        assertTrue("El archivo físico debe ser eliminado tras borrar", !mapFile.exists())
    }

    @Test
    fun test_offline_map_manager_supports_country_bulk_download_and_version_updates() = testScope.runTest {
        val manager = OfflineMapManager(baseDir = tempDir, scope = testScope, ioDispatcher = testDispatcher)

        val baseState = manager.packageStates.value["world-base"] as MapPackageState.Downloaded
        assertEquals("2026.11", baseState.installedVersion)

        manager.checkForUpdates()
        assertTrue(
            "Debe detectar el mapa base desactualizado",
            manager.lastUpdateCheckMessage.value?.contains("1 paquete") == true
        )

        manager.updateAllOutdated(allowCellularOverride = true)
        advanceUntilIdle()

        val upgraded = manager.packageStates.value["world-base"] as MapPackageState.Downloaded
        assertEquals("2026.12", upgraded.installedVersion)

        manager.downloadCountry("España", allowCellularOverride = true)
        advanceUntilIdle()

        assertTrue("Madrid debe quedar descargado", manager.packageStates.value["es-mad"] is MapPackageState.Downloaded)
        assertTrue("Comunidad Valenciana (Morella) debe quedar descargado", manager.packageStates.value["es-val"] is MapPackageState.Downloaded)
    }
}
