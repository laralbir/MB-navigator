package org.mbnavigator.android.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.ActiveRouteSession
import org.mbnavigator.android.core.routing.HazardCategory
import org.mbnavigator.android.core.routing.LiveNavigationProgress
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import org.mbnavigator.android.core.routing.NavigationRunState
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.routing.RouteManeuver
import org.mbnavigator.android.core.routing.TrafficDensityClient
import org.mbnavigator.android.core.routing.TrafficIncidentPoint
import org.mbnavigator.android.core.routing.TurnByTurnEngine
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.traffic.SafetyHazardAlertEngine
import org.mbnavigator.android.core.voice.AndroidTextToSpeechService
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pantalla principal de Android Auto basada en NavigationTemplate.
 * Coordina el mapa proyectado (CarSurfaceMapRenderer), instrucciones en pantalla/Cluster
 * (NavigationManager), alertas de seguridad DGT/radares y avisos por voz TTS.
 */
class MBAutoNavigationScreen(
    carContext: CarContext,
    private val surfaceRenderer: CarSurfaceMapRenderer
) : Screen(carContext), DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationTracker = RealLocationTracker.getInstance(carContext)
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private val ttsService = AndroidTextToSpeechService(carContext)

    private var activeRoute: ActiveRouteSession? = null
    private var navigationState: NavigationRunState = NavigationRunState.IDLE
    private var currentPosition: GeoPoint? = null
    private var currentSpeedKmh: Float = 0f
    private var liveProgress: LiveNavigationProgress? = null
    private var trafficIncidents: List<TrafficIncidentPoint> = emptyList()

    private var lastSpokenPrompt: Pair<Int, Int>? = null
    private val announcedSafetyAlertKeys = mutableSetOf<String>()
    private var lastActiveHazardBannerKey: String? = null
    private var lastRecalcTimestampMs: Long = 0L
    private var isRecalculating: Boolean = false
    private var lastSnappedRouteId: String? = null
    private var lastTrafficFetchMs: Long = 0L
    private var carNavigationStarted: Boolean = false
    private var lastTripUpdateMs: Long = 0L
    private var lastTripManeuverIndex: Int = -1
    private var safetyRefreshJob: Job? = null

    init {
        lifecycle.addObserver(this)
        navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                NavigationSessionRepository.stopNavigation()
                stopCarNavigationIfActive()
                invalidate()
            }

            override fun onAutoDriveEnabled() {
                // Opcional: Android Auto Auto-Drive no necesario si usamos GPS real del móvil
            }
        })
    }

    override fun onCreate(owner: LifecycleOwner) {
        if (locationTracker.hasPermission()) {
            locationTracker.startTracking()
        }

        scope.launch {
            AppPreferencesManager.voiceLanguageTag.collectLatest { tag ->
                val parts = tag.split("-")
                val locale = if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale("es", "ES")
                ttsService.setLanguage(locale)
            }
        }

        scope.launch {
            AppPreferencesManager.voiceSpeechRate.collectLatest { rate ->
                ttsService.setSpeechRate(rate)
            }
        }

        scope.launch {
            NavigationSessionRepository.activeRoute.collectLatest { route ->
                val routeChanged = activeRoute?.id != route?.id
                activeRoute = route
                if (routeChanged) {
                    lastSpokenPrompt = null
                    announcedSafetyAlertKeys.clear()
                    lastActiveHazardBannerKey = null
                    lastTripManeuverIndex = -1
                    refreshTrafficIncidents(force = true)
                    maybeSnapRouteToRoads(route)
                }
                recomputeNavigationState()
            }
        }

        scope.launch {
            NavigationSessionRepository.navigationState.collectLatest { state ->
                navigationState = state
                when (state) {
                    NavigationRunState.NAVIGATING -> startCarNavigationIfNeeded()
                    NavigationRunState.IDLE -> {
                        stopCarNavigationIfActive()
                        lastSpokenPrompt = null
                    }
                    NavigationRunState.READY, NavigationRunState.PAUSED -> Unit
                }
                recomputeNavigationState()
            }
        }

        scope.launch {
            locationTracker.currentLocation.collectLatest { loc ->
                currentPosition = loc
                currentSpeedKmh = (loc?.speedMps ?: 0f) * 3.6f
                recomputeNavigationState()
            }
        }

        scope.launch {
            AppPreferencesManager.trafficLayerEnabled.collectLatest {
                refreshTrafficIncidents(force = true)
                recomputeNavigationState()
            }
        }

        scope.launch {
            AppPreferencesManager.dgtAlertsEnabled.collectLatest {
                refreshTrafficIncidents(force = true)
            }
        }

        scope.launch {
            AppPreferencesManager.speedCameraAlertsEnabled.collectLatest {
                refreshTrafficIncidents(force = true)
            }
        }

        safetyRefreshJob = scope.launch {
            while (isActive) {
                delay(120_000L)
                refreshTrafficIncidents(force = false)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        safetyRefreshJob?.cancel()
        stopCarNavigationIfActive()
        ttsService.shutdown()
        scope.cancel()
    }

    override fun onGetTemplate(): Template {
        val route = activeRoute
        val progress = liveProgress
        val voiceEnabled = AppPreferencesManager.voiceGuidanceEnabled.value

        val topStripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Destinos")
                    .setOnClickListener {
                        screenManager.push(MBAutoDestinationsScreen(carContext))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Buscar")
                    .setOnClickListener {
                        screenManager.push(MBAutoSearchScreen(carContext))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (voiceEnabled) "Voz ON" else "Silencio")
                    .setOnClickListener {
                        val next = !AppPreferencesManager.voiceGuidanceEnabled.value
                        AppPreferencesManager.setVoiceGuidanceEnabled(next)
                        if (!next) {
                            ttsService.stopSpeaking()
                        }
                        CarToast.makeText(
                            carContext,
                            if (next) "Voz activada (alertas siempre activas)" else "Giros silenciados (radares/DGT activos)",
                            CarToast.LENGTH_SHORT
                        ).show()
                        invalidate()
                    }
                    .build()
            )

        if (route != null) {
            val routeActionTitle = if (navigationState == NavigationRunState.NAVIGATING) {
                "Detener"
            } else {
                "Iniciar"
            }
            topStripBuilder.addAction(
                Action.Builder()
                    .setTitle(routeActionTitle)
                    .setBackgroundColor(
                        if (navigationState == NavigationRunState.NAVIGATING) CarColor.RED else CarIconFactory.primaryCarColor()
                    )
                    .setOnClickListener {
                        if (navigationState == NavigationRunState.NAVIGATING) {
                            NavigationSessionRepository.stopNavigation()
                            stopCarNavigationIfActive()
                            ttsService.stopSpeaking()
                        } else {
                            NavigationSessionRepository.startNavigation()
                            startCarNavigationIfNeeded()
                        }
                        invalidate()
                    }
                    .build()
            )
        }

        val mapActionStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(
                Action.Builder()
                    .setIcon(CarIconFactory.buildTextCarIcon("+"))
                    .setOnClickListener { surfaceRenderer.zoomIn() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(CarIconFactory.buildTextCarIcon("−"))
                    .setOnClickListener { surfaceRenderer.zoomOut() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(CarIconFactory.buildTextCarIcon("◎"))
                    .setOnClickListener {
                        surfaceRenderer.recenter()
                        CarToast.makeText(carContext, "Centrado en tu posición", CarToast.LENGTH_SHORT).show()
                    }
                    .build()
            )
            .build()

        val navBuilder = NavigationTemplate.Builder()
            .setActionStrip(topStripBuilder.build())
            .setMapActionStrip(mapActionStrip)
            .setBackgroundColor(CarIconFactory.primaryCarColor())

        if (route != null && navigationState != NavigationRunState.IDLE && progress != null) {
            navBuilder.setNavigationInfo(buildRoutingInfo(progress))
            navBuilder.setDestinationTravelEstimate(buildTravelEstimate(progress))
        }

        return navBuilder.build()
    }

    private fun recomputeNavigationState() {
        val route = activeRoute
        val pos = currentPosition

        val progress = if (route != null && pos != null && route.points.isNotEmpty() && route.maneuvers.isNotEmpty()) {
            TurnByTurnEngine.evaluateProgress(
                currentPosition = pos,
                points = route.points,
                maneuvers = route.maneuvers,
                currentSpeedKmh = currentSpeedKmh
            )
        } else {
            null
        }
        liveProgress = progress

        surfaceRenderer.activeRoute = route
        surfaceRenderer.navigationState = navigationState
        surfaceRenderer.effectivePosition = pos
        surfaceRenderer.effectiveSpeedKmh = currentSpeedKmh
        surfaceRenderer.incidents = trafficIncidents
        surfaceRenderer.updateMapFrame()

        if (navigationState == NavigationRunState.NAVIGATING && route != null && pos != null && progress != null) {
            handleActiveVoiceAndSafety(route, pos, progress)
            pushTripUpdateToCarHost(route, progress)
            if (progress.hasArrived) {
                CarToast.makeText(carContext, "Has llegado a ${route.title}", CarToast.LENGTH_LONG).show()
                if (AppPreferencesManager.voiceGuidanceEnabled.value) {
                    ttsService.speak("Has llegado a tu destino.", flushQueue = true)
                }
                NavigationSessionRepository.stopNavigation()
                stopCarNavigationIfActive()
            } else if (progress.isOffRoute &&
                AppPreferencesManager.autoRecalculateEnabled.value &&
                !isRecalculating &&
                System.currentTimeMillis() - lastRecalcTimestampMs > 12_000L
            ) {
                triggerOffRouteRecalculation(pos, route)
            }
        }

        invalidate()
    }

    private fun handleActiveVoiceAndSafety(
        route: ActiveRouteSession,
        pos: GeoPoint,
        progress: LiveNavigationProgress
    ) {
        val voiceEnabled = AppPreferencesManager.voiceGuidanceEnabled.value
        val dgtEnabled = AppPreferencesManager.dgtAlertsEnabled.value
        val radarEnabled = AppPreferencesManager.speedCameraAlertsEnabled.value

        // 1. Alertas de seguridad DGT + radares (suenan siempre si están activadas aunque la voz de giros esté silenciada)
        if (trafficIncidents.isNotEmpty() && (dgtEnabled || radarEnabled)) {
            val alert = SafetyHazardAlertEngine.evaluateActiveHazard(
                currentPosition = pos,
                routePoints = route.points,
                incidents = trafficIncidents,
                dgtAlertsEnabled = dgtEnabled,
                speedCameraAlertsEnabled = radarEnabled
            )
            if (alert != null) {
                val dedupKey = SafetyHazardAlertEngine.buildAlertDeduplicationKey(alert)
                if (dedupKey != lastActiveHazardBannerKey) {
                    lastActiveHazardBannerKey = dedupKey
                    val detail = alert.incident.roadName.ifBlank { alert.incident.description }.ifBlank { "En ruta" }
                    CarToast.makeText(
                        carContext,
                        "${alert.incident.category.badgeIcon} ${alert.incident.category.label}: $detail (${alert.distanceMeters.roundToInt()} m)",
                        CarToast.LENGTH_LONG
                    ).show()
                }
                if (SafetyHazardAlertEngine.shouldAnnounceSafetyAlert(
                        alert = alert,
                        dgtAlertsEnabled = dgtEnabled,
                        speedCameraAlertsEnabled = radarEnabled,
                        announcedKeys = announcedSafetyAlertKeys
                    )
                ) {
                    announcedSafetyAlertKeys.add(dedupKey)
                    ttsService.playSafetyAlertTone(
                        urgent = alert.incident.category == HazardCategory.ACCIDENT ||
                            alert.incident.category == HazardCategory.SPEED_CAMERA ||
                            alert.incident.category == HazardCategory.BROKEN_DOWN_VEHICLE
                    )
                    ttsService.speak(SafetyHazardAlertEngine.formatHazardSpeech(alert), flushQueue = true)
                }
            }
        }

        // 2. Guía por voz paso a paso
        if (voiceEnabled && !progress.isOffRoute) {
            val threshold = TurnByTurnEngine.determineVoicePromptThreshold(
                maneuverIndex = progress.currentManeuver.index,
                distanceMeters = progress.distanceToManeuverMeters,
                lastSpokenPair = lastSpokenPrompt
            )
            if (threshold != null) {
                lastSpokenPrompt = progress.currentManeuver.index to threshold
                val spoken = TurnByTurnEngine.formatSpokenInstruction(
                    maneuver = progress.currentManeuver,
                    distanceMeters = progress.distanceToManeuverMeters
                )
                ttsService.speak(spoken, flushQueue = false)
            }
        }
    }

    private fun triggerOffRouteRecalculation(origin: GeoPoint, route: ActiveRouteSession) {
        val destination = route.points.lastOrNull() ?: return
        lastRecalcTimestampMs = System.currentTimeMillis()
        isRecalculating = true

        if (AppPreferencesManager.voiceGuidanceEnabled.value) {
            ttsService.speak("Fuera de ruta. Recalculando.", flushQueue = true)
        }

        scope.launch {
            try {
                val recalculated = OpenRoutingClient.calculateRoadRoute(
                    origin = origin,
                    destination = destination,
                    destinationName = route.title,
                    profile = MotorcycleRoutingProfile.CURVY,
                    options = AppPreferencesManager.routeOptions.value
                )
                NavigationSessionRepository.startCalculatedRoute(
                    result = recalculated,
                    waypoints = route.waypoints,
                    autoStart = true
                )
            } catch (_: Exception) {
                // Silencioso; reintentará pasado el cooldown si continúa fuera de ruta
            } finally {
                isRecalculating = false
            }
        }
    }

    private fun maybeSnapRouteToRoads(route: ActiveRouteSession?) {
        if (route == null || route.id == lastSnappedRouteId) return
        if (!OpenRoutingClient.needsRoadSnapping(route.points)) return
        lastSnappedRouteId = route.id

        scope.launch {
            val snapped = OpenRoutingClient.snapRouteToRoads(
                points = route.points,
                routeTitle = route.title,
                viaPoints = route.waypoints,
                profile = MotorcycleRoutingProfile.CURVY,
                options = AppPreferencesManager.routeOptions.value
            )
            if (snapped != null && snapped.points.size > route.points.size) {
                NavigationSessionRepository.applyRoadSnappedGeometry(route.id, snapped)
            }
        }
    }

    private fun refreshTrafficIncidents(force: Boolean) {
        val needIncidents = AppPreferencesManager.trafficLayerEnabled.value ||
            AppPreferencesManager.dgtAlertsEnabled.value ||
            AppPreferencesManager.speedCameraAlertsEnabled.value

        if (!needIncidents) {
            trafficIncidents = emptyList()
            surfaceRenderer.incidents = emptyList()
            surfaceRenderer.updateMapFrame()
            return
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastTrafficFetchMs < 90_000L) return
        lastTrafficFetchMs = now

        val center = currentPosition
            ?: activeRoute?.points?.firstOrNull()
            ?: GeoPoint(40.4168, -3.7038)

        scope.launch {
            trafficIncidents = TrafficDensityClient.fetchNearbyIncidents(
                center = center,
                radiusKm = 150.0,
                includeSpeedCameras = AppPreferencesManager.speedCameraAlertsEnabled.value ||
                    AppPreferencesManager.trafficLayerEnabled.value
            )
            surfaceRenderer.incidents = trafficIncidents
            surfaceRenderer.updateMapFrame()
        }
    }

    private fun buildRoutingInfo(progress: LiveNavigationProgress): RoutingInfo {
        val step = buildCarStep(progress.currentManeuver, progress.isOffRoute)
        val distance = toCarDistance(
            if (progress.isOffRoute) progress.offRouteMeters else progress.distanceToManeuverMeters
        )
        val builder = RoutingInfo.Builder()
            .setCurrentStep(step, distance)
            .setLoading(isRecalculating)

        val next = progress.nextManeuverAfter
        if (next != null && !progress.isOffRoute) {
            builder.setNextStep(buildCarStep(next, isOffRoute = false))
        }
        return builder.build()
    }

    private fun buildCarStep(maneuver: RouteManeuver, isOffRoute: Boolean): Step {
        val carManeuver = Maneuver.Builder(CarIconFactory.toCarManeuverType(maneuver.type))
            .setIcon(CarIconFactory.buildManeuverCarIcon(maneuver.type, isOffRoute = isOffRoute))
            .build()
        val text = if (isOffRoute) {
            "Fuera de ruta · Recalculando"
        } else {
            maneuver.instruction
        }
        val builder = Step.Builder(text)
            .setManeuver(carManeuver)
        if (maneuver.roadName.isNotBlank() && !isOffRoute) {
            builder.setRoad(maneuver.roadName)
        }
        return builder.build()
    }

    private fun buildTravelEstimate(progress: LiveNavigationProgress): TravelEstimate {
        val arrivalTime = ZonedDateTime.now().plusMinutes(progress.remainingMinutes.toLong().coerceAtLeast(1L))
        return TravelEstimate.Builder(
            toCarDistance(progress.remainingDistanceMeters),
            arrivalTime
        )
            .setRemainingTimeSeconds(progress.remainingMinutes.toLong().coerceAtLeast(1L) * 60L)
            .build()
    }

    private fun toCarDistance(meters: Double): Distance {
        val safeMeters = meters.coerceAtLeast(0.0)
        return if (safeMeters >= 1000.0) {
            val kmRounded = (safeMeters / 100.0).roundToInt() / 10.0
            Distance.create(kmRounded, Distance.UNIT_KILOMETERS_P1)
        } else {
            val mRounded = ((safeMeters / 10.0).roundToInt() * 10).coerceAtLeast(0)
            Distance.create(mRounded.toDouble(), Distance.UNIT_METERS)
        }
    }

    private fun startCarNavigationIfNeeded() {
        if (carNavigationStarted) return
        try {
            navigationManager.navigationStarted()
            carNavigationStarted = true
        } catch (_: IllegalStateException) {
            // El host todavía no está en estado válido o ya finalizó
        }
    }

    private fun stopCarNavigationIfActive() {
        if (!carNavigationStarted) return
        try {
            navigationManager.navigationEnded()
        } catch (_: IllegalStateException) {
            // Ignorar si la sesión ya se cerró
        } finally {
            carNavigationStarted = false
        }
    }

    private fun pushTripUpdateToCarHost(route: ActiveRouteSession, progress: LiveNavigationProgress) {
        if (!carNavigationStarted) return
        val now = System.currentTimeMillis()
        val maneuverChanged = progress.currentManeuver.index != lastTripManeuverIndex
        if (!maneuverChanged && now - lastTripUpdateMs < 2_500L) return

        lastTripUpdateMs = now
        lastTripManeuverIndex = progress.currentManeuver.index

        val currentStep = buildCarStep(progress.currentManeuver, progress.isOffRoute)
        val stepEstimate = TravelEstimate.Builder(
            toCarDistance(progress.distanceToManeuverMeters),
            ZonedDateTime.now().plusSeconds(15)
        ).build()

        val destinationObj = androidx.car.app.navigation.model.Destination.Builder()
            .setName(route.title.ifBlank { "Destino" })
            .build()
        val destEstimate = buildTravelEstimate(progress)

        val trip = Trip.Builder()
            .addDestination(destinationObj, destEstimate)
            .addStep(currentStep, stepEstimate)
            .apply {
                val road = progress.currentManeuver.roadName
                if (road.isNotBlank()) {
                    setCurrentRoad(road)
                }
            }
            .setLoading(isRecalculating)
            .build()

        try {
            navigationManager.updateTrip(trip)
        } catch (_: IllegalStateException) {
            // Ignorar si NavigationManager no aceptó el update transitoriamente
        }
    }
}
