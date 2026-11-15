package org.mbnavigator.android.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.settings.AppPreferencesManager
import java.util.Locale

/**
 * Menú de Destinos Favoritos, Rutas GPX guardadas y Preferencias de cálculo dentro de Android Auto.
 */
class MBAutoDestinationsScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isCalculating: Boolean = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (isCalculating) {
            return ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle("Calculando ruta en moto...")
                .setLoading(true)
                .build()
        }

        val favorites = AppPreferencesManager.favoriteDestinations.value
        val savedRoutes = NavigationSessionRepository.savedRoutes.value
        val opts = AppPreferencesManager.routeOptions.value

        val favList = ItemList.Builder()
        if (favorites.isEmpty()) {
            favList.addItem(
                Row.Builder()
                    .setTitle("Buscar destino nuevo")
                    .addText("Configura Casa o Trabajo en Ajustes del móvil")
                    .setOnClickListener {
                        screenManager.push(MBAutoSearchScreen(carContext))
                    }
                    .build()
            )
        } else {
            favorites.take(3).forEach { fav ->
                favList.addItem(
                    Row.Builder()
                        .setTitle("${fav.category.icon} ${fav.name}")
                        .addText(fav.address.ifBlank { fav.category.label })
                        .setOnClickListener {
                            calculateToFavorite(GeoPoint(fav.latitude, fav.longitude), fav.name)
                        }
                        .build()
                )
            }
        }

        val routesList = ItemList.Builder()
        savedRoutes.take(3).forEach { entry ->
            routesList.addItem(
                Row.Builder()
                    .setTitle(entry.title)
                    .addText("${String.format(Locale.US, "%.1f", entry.distanceKm)} km · +${entry.elevationGainM.toInt()} m")
                    .setOnClickListener {
                        NavigationSessionRepository.startNavigatingRoute(entry, previewSimulation = false, autoStart = true)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        val optsSummary = buildString {
            append(if (opts.allowHighways) "Autovías: Sí" else "Autovías: No")
            append(" · ")
            append(if (opts.allowTolls) "Peajes: Sí" else "Peajes: No")
            append(" · ")
            append(if (opts.allowUnpavedTracks) "Caminos: Sí" else "Asfalto")
        }

        val quickActionsList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("🔍 Buscar destino por voz o teclado")
                    .setOnClickListener { screenManager.push(MBAutoSearchScreen(carContext)) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(if (opts.allowHighways) "🛣️ Evitar Autovías" else "🛣️ Permitir Autovías")
                    .addText(optsSummary)
                    .setOnClickListener {
                        AppPreferencesManager.updateRouteOptions { copy(allowHighways = !allowHighways) }
                        CarToast.makeText(carContext, "Preferencia de ruta actualizada", CarToast.LENGTH_SHORT).show()
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setHeaderAction(Action.BACK)
            .setTitle("Destinos y Rutas")
            .addSectionedList(SectionedItemList.create(quickActionsList, "Acciones rápidas"))
            .addSectionedList(SectionedItemList.create(favList.build(), "Favoritos"))
            .addSectionedList(SectionedItemList.create(routesList.build(), "Rutas GPX guardadas"))
            .build()
    }

    private fun calculateToFavorite(dest: GeoPoint, name: String) {
        scope.launch {
            isCalculating = true
            invalidate()
            val tracker = RealLocationTracker.getInstance(carContext)
            val origin = tracker.currentLocation.value ?: GeoPoint(40.4168, -3.7038)
            val options = AppPreferencesManager.routeOptions.value
            val result = OpenRoutingClient.calculateRoadRoute(
                origin = origin,
                destination = dest,
                destinationName = name,
                options = options
            )
            NavigationSessionRepository.startCalculatedRoute(result, autoStart = true)
            isCalculating = false
            screenManager.pop()
        }
    }
}
