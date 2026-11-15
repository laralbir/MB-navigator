package org.mbnavigator.android.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Sesión principal de MB Navigator en Android Auto / pantalla de coche o moto.
 * Sincroniza preferencias, rutas y mapa OSM con la interfaz móvil.
 */
class MBNavigatorCarSession : Session() {

    private var surfaceRenderer: CarSurfaceMapRenderer? = null
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateScreen(intent: Intent): Screen {
        val appContext = carContext.applicationContext
        AppPreferencesManager.init(appContext)
        NavigationSessionRepository.ensureInitialized(appContext)
        initOsmDroidIfNeeded()

        val renderer = CarSurfaceMapRenderer(carContext)
        surfaceRenderer = renderer
        lifecycle.addObserver(renderer)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                sessionScope.cancel()
            }
        })

        handleCarIntent(intent)
        return MBAutoNavigationScreen(carContext, renderer)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCarIntent(intent)
    }

    private fun initOsmDroidIfNeeded() {
        val config = Configuration.getInstance()
        config.userAgentValue = "MBNavigator-AndroidAuto/1.0"
        val cacheDir = File(carContext.cacheDir, "osmdroid")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        config.osmdroidBasePath = carContext.cacheDir
        config.osmdroidTileCache = cacheDir
    }

    private fun handleCarIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != CarContext.ACTION_NAVIGATE && action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return

        val raw = data.toString()
        val coordsMatch = Regex("""(-?\d+\.\d+)\s*,\s*(-?\d+\.\d+)""").find(raw)
        if (coordsMatch != null) {
            val lat = coordsMatch.groupValues[1].toDoubleOrNull()
            val lon = coordsMatch.groupValues[2].toDoubleOrNull()
            val queryLabel = data.getQueryParameter("q")?.takeIf { it.isNotBlank() } ?: "Destino de voz"
            if (lat != null && lon != null) {
                launchRouteTo(GeoPoint(lat, lon), queryLabel)
                return
            }
        }

        val query = data.getQueryParameter("q")
            ?: raw.removePrefix("google.navigation:q=").takeIf { it.isNotBlank() }
        if (!query.isNullOrBlank()) {
            sessionScope.launch {
                val results = OpenRoutingClient.searchDestination(query).getOrDefault(emptyList())
                val first = results.firstOrNull() ?: return@launch
                launchRouteTo(first.location, first.displayName)
            }
        }
    }

    private fun launchRouteTo(destination: GeoPoint, label: String) {
        val tracker = RealLocationTracker.getInstance(carContext)
        val origin = tracker.currentLocation.value
            ?: NavigationSessionRepository.activeRoute.value?.points?.firstOrNull()
            ?: GeoPoint(40.4168, -3.7038)

        sessionScope.launch {
            try {
                val calculated = OpenRoutingClient.calculateRoadRoute(
                    origin = origin,
                    destination = destination,
                    destinationName = label,
                    profile = MotorcycleRoutingProfile.CURVY,
                    options = AppPreferencesManager.routeOptions.value
                )
                NavigationSessionRepository.startCalculatedRoute(
                    result = calculated,
                    autoStart = true
                )
            } catch (_: Exception) {
                // Si no hay conectividad transitoria, permanece en pantalla principal
            }
        }
    }
}
