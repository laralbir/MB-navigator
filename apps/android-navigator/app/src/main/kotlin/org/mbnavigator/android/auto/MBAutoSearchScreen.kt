package org.mbnavigator.android.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.GeocodedPlace
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.settings.AppPreferencesManager

/**
 * Pantalla de búsqueda por voz/teclado en Android Auto (SearchTemplate) con cálculo inmediato en moto.
 */
class MBAutoSearchScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var isLoading: Boolean = false
    private var queryText: String = ""
    private var results: List<GeocodedPlace> = emptyList()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
            .setNoItemsMessage(
                if (queryText.isBlank()) "Di o escribe un destino (ciudad, puerto, dirección)"
                else "Sin resultados para \"$queryText\""
            )

        results.take(6).forEach { place ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(place.shortName)
                    .addText(place.displayName)
                    .setOnClickListener {
                        startRouteTo(place.location, place.shortName)
                    }
                    .build()
            )
        }

        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                queryText = searchText
            }

            override fun onSearchSubmitted(searchText: String) {
                queryText = searchText
                performSearch(searchText)
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Buscar destino en moto...")
            .setShowKeyboardByDefault(false)
            .setLoading(isLoading)

        if (!isLoading) {
            builder.setItemList(listBuilder.build())
        }
        return builder.build()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = scope.launch {
            isLoading = true
            invalidate()
            OpenRoutingClient.searchDestination(query)
                .onSuccess { found ->
                    results = found
                }
                .onFailure {
                    CarToast.makeText(carContext, "Error buscando destino", CarToast.LENGTH_SHORT).show()
                }
            isLoading = false
            invalidate()
        }
    }

    private fun startRouteTo(destination: GeoPoint, name: String) {
        scope.launch {
            isLoading = true
            invalidate()
            val tracker = RealLocationTracker.getInstance(carContext)
            val origin = tracker.currentLocation.value ?: GeoPoint(40.4168, -3.7038)
            val options = AppPreferencesManager.routeOptions.value
            val result = OpenRoutingClient.calculateRoadRoute(
                origin = origin,
                destination = destination,
                destinationName = name,
                options = options
            )
            NavigationSessionRepository.startCalculatedRoute(result, autoStart = true)
            isLoading = false
            screenManager.popToRoot()
        }
    }
}
