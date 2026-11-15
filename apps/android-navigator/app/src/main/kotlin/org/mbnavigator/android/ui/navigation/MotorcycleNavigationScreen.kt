package org.mbnavigator.android.ui.navigation

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.ActiveRouteSession
import org.mbnavigator.android.core.routing.GeocodedPlace
import org.mbnavigator.android.core.routing.GpxKmlParser
import org.mbnavigator.android.core.routing.LiveNavigationProgress
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import org.mbnavigator.android.core.routing.NavigationDisplayCalculator
import org.mbnavigator.android.core.routing.NavigationRunState
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.routing.SavedRouteEntry
import org.mbnavigator.android.core.routing.TrafficDensityClient
import org.mbnavigator.android.core.routing.TrafficIncidentPoint
import org.mbnavigator.android.core.routing.TurnByTurnEngine
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.FavoriteDestination
import org.mbnavigator.android.core.settings.FavoritePlaceCategory
import org.mbnavigator.android.core.settings.MapColorStyle
import org.mbnavigator.android.core.settings.MapOrientationMode
import org.mbnavigator.android.core.settings.NavigatorPalette
import org.mbnavigator.android.core.settings.RouteCalculationOptions
import org.mbnavigator.android.core.settings.RouteTrackColor
import org.mbnavigator.android.core.settings.rememberEffectiveMapStyle
import org.mbnavigator.android.core.settings.rememberNavigatorPalette
import org.mbnavigator.android.core.voice.AndroidTextToSpeechService
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint as OsmGeoPoint

private val TileSourceOsmHot by lazy {
    XYTileSource(
        "OsmHumanitarian", 0, 19, 256, ".png",
        arrayOf(
            "https://a.tile.openstreetmap.fr/hot/",
            "https://b.tile.openstreetmap.fr/hot/",
            "https://c.tile.openstreetmap.fr/hot/"
        )
    )
}

private val TileSourceOsmDeRutas by lazy {
    // Servidor rápido alemán/europeo OSM con alto contraste en carreteras nacionales, secundarias y puertos
    XYTileSource(
        "OsmDeRutas", 0, 19, 256, ".png",
        arrayOf("https://tile.openstreetmap.de/")
    )
}

@Composable
fun MotorcycleNavigationScreen(
    showHamburgerMenu: Boolean = false,
    onOpenHamburgerMenu: () -> Unit = {},
    onOpenGpsDiagnostics: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val locationTracker = remember { RealLocationTracker.getInstance(context) }
    val ttsService = remember { AndroidTextToSpeechService(context) }

    val realLocation by locationTracker.currentLocation.collectAsState()
    val palette = rememberNavigatorPalette(realLocation)
    val effectiveMapStyle = rememberEffectiveMapStyle(realLocation)

    val hasGpsFix by locationTracker.hasGpsFix.collectAsState()
    val gnssSummary by locationTracker.gnssSummary.collectAsState()
    val activeRoute by NavigationSessionRepository.activeRoute.collectAsState()
    val navigationState by NavigationSessionRepository.navigationState.collectAsState()
    val routeColor by AppPreferencesManager.routeColor.collectAsState()
    val mapOrientationMode by AppPreferencesManager.mapOrientationMode.collectAsState()
    val speedAutoZoom by AppPreferencesManager.speedAutoZoomEnabled.collectAsState()
    val trafficLayerEnabled by AppPreferencesManager.trafficLayerEnabled.collectAsState()
    val autoRecalculate by AppPreferencesManager.autoRecalculateEnabled.collectAsState()
    val routeOptions by AppPreferencesManager.routeOptions.collectAsState()
    val favoriteDestinations by AppPreferencesManager.favoriteDestinations.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(locationTracker.hasPermission()) }
    var followGps by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var selectedProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }

    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var isAutoRecalculating by remember { mutableStateOf(false) }
    var lastAutoRecalcAtMs by remember { mutableLongStateOf(0L) }
    var manualZoomPauseUntilMs by remember { mutableLongStateOf(0L) }
    var trafficIncidents by remember { mutableStateOf<List<TrafficIncidentPoint>>(emptyList()) }
    var lastAppliedTrafficKey by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var previewIndex by remember(activeRoute?.id, activeRoute?.isPreviewSimulation) { mutableIntStateOf(0) }
    var previewPlaying by remember(activeRoute?.id, activeRoute?.isPreviewSimulation) {
        mutableStateOf(activeRoute?.isPreviewSimulation == true)
    }

    var lastSpokenPrompt by remember(activeRoute?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastRenderedRouteKey by remember { mutableStateOf<String?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            locationTracker.startTracking()
        }
    }

    LaunchedEffect(Unit) {
        AppPreferencesManager.init(context)
        NavigationSessionRepository.ensureInitialized(context)
        Configuration.getInstance().apply {
            load(context.applicationContext, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "MBNavigator-MotoGPS/1.0"
        }

        if (hasLocationPermission) {
            locationTracker.startTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsService.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val map = mapViewRef
        if (map == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> map.onResume()
                    Lifecycle.Event.ON_PAUSE -> map.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                map.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                map.onPause()
            }
        }
    }

    LaunchedEffect(navigationState) {
        if (navigationState == NavigationRunState.NAVIGATING) {
            followGps = true
        }
    }

    LaunchedEffect(activeRoute?.id, activeRoute?.isPreviewSimulation, previewPlaying, navigationState) {
        val route = activeRoute
        val canSimulate = navigationState == NavigationRunState.NAVIGATING || navigationState == NavigationRunState.READY
        if (route != null && route.isPreviewSimulation && previewPlaying && canSimulate && route.points.size > 1) {
            while (previewPlaying) {
                delay(750L)
                if (navigationState == NavigationRunState.PAUSED) continue
                val step = (route.points.size / 160).coerceAtLeast(1)
                val next = previewIndex + step
                if (next >= route.points.lastIndex) {
                    previewIndex = route.points.lastIndex
                    previewPlaying = false
                } else {
                    previewIndex = next
                }
            }
        }
    }

    val effectivePosition: GeoPoint? = remember(realLocation, activeRoute, previewIndex) {
        val route = activeRoute
        if (route != null && route.isPreviewSimulation && route.points.isNotEmpty()) {
            route.points[previewIndex.coerceIn(0, route.points.lastIndex)]
        } else {
            realLocation
        }
    }

    val effectiveSpeedKmh = remember(effectivePosition, activeRoute, previewPlaying) {
        val route = activeRoute
        if (route != null && route.isPreviewSimulation && previewPlaying) {
            72f
        } else {
            (effectivePosition?.speedMps ?: 0f) * 3.6f
        }
    }

    val liveProgress: LiveNavigationProgress? = remember(effectivePosition, activeRoute, effectiveSpeedKmh) {
        val route = activeRoute
        val pos = effectivePosition
        if (route != null && pos != null && route.points.isNotEmpty()) {
            TurnByTurnEngine.evaluateProgress(
                currentPosition = pos,
                points = route.points,
                maneuvers = route.maneuvers,
                currentSpeedKmh = effectiveSpeedKmh
            )
        } else {
            null
        }
    }

    LaunchedEffect(
        liveProgress?.currentManeuver?.index,
        liveProgress?.distanceToManeuverMeters,
        voiceEnabled,
        navigationState
    ) {
        val progress = liveProgress ?: return@LaunchedEffect
        if (!voiceEnabled || activeRoute == null || navigationState != NavigationRunState.NAVIGATING) {
            return@LaunchedEffect
        }

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
            ttsService.speak(spoken, flushQueue = true)
        }
    }

    // Recálculo automático si vas en marcha y te sales de ruta
    LaunchedEffect(liveProgress?.isOffRoute, navigationState, activeRoute?.id, autoRecalculate) {
        val route = activeRoute ?: return@LaunchedEffect
        val pos = realLocation ?: return@LaunchedEffect
        if (navigationState != NavigationRunState.NAVIGATING ||
            route.isPreviewSimulation ||
            !autoRecalculate ||
            liveProgress?.isOffRoute != true ||
            isAutoRecalculating
        ) {
            return@LaunchedEffect
        }

        val now = System.currentTimeMillis()
        if ((now - lastAutoRecalcAtMs) < 20_000L) return@LaunchedEffect

        // Esperar 3.5 s para confirmar desviación real y evitar falsos positivos en túneles o cruces
        delay(3500L)
        if (navigationState != NavigationRunState.NAVIGATING || liveProgress.isOffRoute != true) {
            return@LaunchedEffect
        }

        val latestPos = realLocation ?: pos
        val dest = route.points.lastOrNull() ?: return@LaunchedEffect
        isAutoRecalculating = true
        lastAutoRecalcAtMs = System.currentTimeMillis()
        if (voiceEnabled) {
            ttsService.speak("Fuera de ruta. Recalculando recorrido.", flushQueue = true)
        }
        val recalculated = OpenRoutingClient.calculateRoadRoute(
            origin = latestPos,
            destination = dest,
            destinationName = route.title.removePrefix("Ruta a ").removePrefix("Ruta Directa (Offline) a "),
            viaPoints = route.waypoints,
            profile = selectedProfile,
            options = routeOptions
        )
        NavigationSessionRepository.startCalculatedRoute(
            result = recalculated,
            waypoints = route.waypoints,
            autoStart = true
        )
        isAutoRecalculating = false
    }

    // Capa de densidad e incidencias de tráfico abiertas DGT
    LaunchedEffect(trafficLayerEnabled, activeRoute?.id, realLocation != null) {
        if (!trafficLayerEnabled) {
            trafficIncidents = emptyList()
            return@LaunchedEffect
        }
        val center = realLocation
            ?: activeRoute?.points?.firstOrNull()
            ?: GeoPoint(40.4168, -3.7038)
        trafficIncidents = TrafficDensityClient.fetchNearbyIncidents(center, radiusKm = 150.0)
    }

    // Dibujar / actualizar capas del mapa OSMDroid (estilo, tráfico, ruta, rotación y zoom por velocidad)
    LaunchedEffect(
        mapViewRef,
        activeRoute,
        effectivePosition,
        followGps,
        effectiveMapStyle,
        routeColor,
        mapOrientationMode,
        speedAutoZoom,
        trafficLayerEnabled,
        trafficIncidents,
        navigationState,
        effectiveSpeedKmh
    ) {
        val map = mapViewRef ?: return@LaunchedEffect
        applyMapVisualStyle(map, effectiveMapStyle)

        val trafficKey = if (trafficLayerEnabled) {
            "on_${trafficIncidents.size}_${trafficIncidents.firstOrNull()?.id.orEmpty()}"
        } else {
            "off"
        }
        val routeKey = activeRoute?.let { "${it.id}_${it.points.size}_${routeColor.name}" }
        val overlaysChanged = (routeKey != lastRenderedRouteKey) || (trafficKey != lastAppliedTrafficKey)

        if (overlaysChanged) {
            val structuralRouteChange = routeKey != lastRenderedRouteKey
            lastRenderedRouteKey = routeKey
            lastAppliedTrafficKey = trafficKey
            map.overlays.clear()

            val route = activeRoute
            if (route != null && route.points.isNotEmpty()) {
                val osmPoints = route.points.map { OsmGeoPoint(it.latitude, it.longitude) }

                val borderLine = Polyline(map).apply {
                    setPoints(osmPoints)
                    outlinePaint.color = android.graphics.Color.parseColor(routeColor.borderHex)
                    outlinePaint.strokeWidth = 22f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                val mainLine = Polyline(map).apply {
                    setPoints(osmPoints)
                    outlinePaint.color = android.graphics.Color.parseColor(routeColor.mainHex)
                    outlinePaint.strokeWidth = 13f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                map.overlays.add(borderLine)
                map.overlays.add(mainLine)

                if (trafficLayerEnabled) {
                    val trafficSegs = TrafficDensityClient.buildRouteTrafficSegments(route.points, trafficIncidents)
                    trafficSegs.forEach { seg ->
                        val tLine = Polyline(map).apply {
                            setPoints(
                                listOf(
                                    OsmGeoPoint(seg.start.latitude, seg.start.longitude),
                                    OsmGeoPoint(seg.end.latitude, seg.end.longitude)
                                )
                            )
                            outlinePaint.color = android.graphics.Color.parseColor(seg.level.colorHex)
                            outlinePaint.strokeWidth = 7f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                        }
                        map.overlays.add(tLine)
                    }
                }

                val startMarker = Marker(map).apply {
                    position = osmPoints.first()
                    title = "Salida: ${route.title}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createBadgeDrawable(context, "IN", android.graphics.Color.parseColor("#0284C7"))
                }
                map.overlays.add(startMarker)

                route.waypoints.forEachIndexed { idx, wp ->
                    val wpColor = if (wp.isMandatoryPassThrough) {
                        android.graphics.Color.parseColor("#7E57C2")
                    } else {
                        android.graphics.Color.parseColor("#64748B")
                    }
                    val wpMarker = Marker(map).apply {
                        position = OsmGeoPoint(wp.point.latitude, wp.point.longitude)
                        title = wp.name
                        snippet = wp.description ?: if (wp.isMandatoryPassThrough) "ViaPoint obligatorio" else "Shaping Point"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createBadgeDrawable(context, "${idx + 1}", wpColor)
                    }
                    map.overlays.add(wpMarker)
                }

                val endMarker = Marker(map).apply {
                    position = osmPoints.last()
                    title = "Destino: ${route.title}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createBadgeDrawable(context, "FIN", android.graphics.Color.parseColor("#DC2626"))
                }
                map.overlays.add(endMarker)

                if (structuralRouteChange) {
                    if (osmPoints.size >= 2) {
                        val bbox = BoundingBox.fromGeoPoints(osmPoints)
                        map.post {
                            try {
                                map.zoomToBoundingBox(bbox, true, 120)
                            } catch (_: Exception) {
                                map.controller.setCenter(osmPoints.first())
                                map.controller.setZoom(13.5)
                            }
                        }
                        followGps = route.isPreviewSimulation || navigationState == NavigationRunState.NAVIGATING
                    } else {
                        map.controller.setCenter(osmPoints.first())
                        map.controller.setZoom(15.0)
                    }
                }
            }

            if (trafficLayerEnabled && trafficIncidents.isNotEmpty()) {
                trafficIncidents.take(35).forEach { inc ->
                    val incMarker = Marker(map).apply {
                        position = OsmGeoPoint(inc.point.latitude, inc.point.longitude)
                        title = "🚦 Tráfico ${inc.level.label}"
                        snippet = inc.description
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createBadgeDrawable(
                            context,
                            "!",
                            android.graphics.Color.parseColor(inc.level.colorHex)
                        )
                    }
                    map.overlays.add(incMarker)
                }
            }
        }

        val cursorTag = "MOTO_CURSOR"
        val existingCursor = map.overlays.filterIsInstance<Marker>().find { it.id == cursorTag }
        val bearing = if (activeRoute?.isPreviewSimulation == true) {
            computePreviewBearing(activeRoute, previewIndex)
        } else {
            effectivePosition?.bearingDegrees ?: 0f
        }

        if (effectivePosition != null) {
            val posOsm = OsmGeoPoint(effectivePosition.latitude, effectivePosition.longitude)

            val cursorMarker = existingCursor ?: Marker(map).apply {
                id = cursorTag
                title = "Tu moto"
                isFlat = true
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createMotoArrowDrawable(context)
                map.overlays.add(this)
            }
            cursorMarker.isFlat = true
            cursorMarker.position = posOsm
            cursorMarker.rotation = -bearing

            if (followGps) {
                map.controller.animateTo(posOsm)

                val targetOrientation = if (mapOrientationMode == MapOrientationMode.COURSE_UP) {
                    -bearing
                } else {
                    0f
                }
                if (kotlin.math.abs(map.mapOrientation - targetOrientation) > 2f) {
                    map.mapOrientation = targetOrientation
                }

                val nowMs = System.currentTimeMillis()
                if (speedAutoZoom &&
                    navigationState == NavigationRunState.NAVIGATING &&
                    nowMs > manualZoomPauseUntilMs
                ) {
                    val targetZoom = NavigationDisplayCalculator.computeTargetZoomForSpeed(effectiveSpeedKmh)
                    if (NavigationDisplayCalculator.shouldUpdateAutoZoom(map.zoomLevelDouble, targetZoom)) {
                        map.controller.setZoom(targetZoom)
                    }
                } else if (map.zoomLevelDouble < 14.0 && !overlaysChanged) {
                    map.controller.setZoom(15.5)
                }
            }
        } else if (mapOrientationMode == MapOrientationMode.NORTH_UP && map.mapOrientation != 0f) {
            map.mapOrientation = 0f
        }
        map.invalidate()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        // 1. MAPA REAL OSMDROID A TODO COLOR
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Inicializar configuración OSMDroid síncronamente antes de crear MapView/TileProvider
                Configuration.getInstance().apply {
                    load(ctx.applicationContext, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    userAgentValue = "MBNavigator-MotoGPS/1.0"
                }
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(6.5)
                    controller.setCenter(OsmGeoPoint(40.4168, -3.7038))
                    applyMapVisualStyle(this, effectiveMapStyle)

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (event != null && (event.x != 0 || event.y != 0)) {
                                followGps = false
                            }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean = false
                    })
                    onResume()
                    mapViewRef = this
                }
            }
        )

        // 2. ZONA SUPERIOR: Barra estado/búsqueda + Maniobra Turn-by-Turn
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showHamburgerMenu) {
                    Surface(
                        modifier = Modifier
                            .size(50.dp)
                            .clickable { onOpenHamburgerMenu() },
                        color = palette.hudOverlay,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, palette.primary),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "☰",
                                color = palette.primary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .clickable { onOpenGpsDiagnostics() },
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (hasGpsFix) palette.success else palette.secondary
                    ),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "📡", fontSize = 16.sp)
                        Column {
                            Text(
                                text = if (activeRoute?.isPreviewSimulation == true) "SIM"
                                else if (hasGpsFix) "GPS 3D"
                                else "BUSCANDO",
                                color = if (hasGpsFix) palette.success else palette.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${gnssSummary.usedInFixCount}/${gnssSummary.totalVisible} sats",
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clickable { showSearchSheet = true },
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeRoute?.title ?: "🔍 Buscar destino o puerto...",
                            color = if (activeRoute != null) palette.textPrimary else palette.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeRoute != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✕",
                                color = palette.danger,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        NavigationSessionRepository.clearActiveRoute()
                                        lastRenderedRouteKey = null
                                        mapViewRef?.overlays?.clear()
                                        mapViewRef?.invalidate()
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Selector rápido de color/estilo de mapa
                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .clickable { AppPreferencesManager.cycleMapStyle() },
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = effectiveMapStyle.shortLabel,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val route = activeRoute
            if (route != null) {
                TurnByTurnBannerCard(
                    route = route,
                    progress = liveProgress,
                    hasGpsFix = hasGpsFix || route.isPreviewSimulation,
                    palette = palette,
                    routeColor = routeColor,
                    onSpeakNow = {
                        val prog = liveProgress
                        if (prog != null) {
                            ttsService.speak(
                                TurnByTurnEngine.formatSpokenInstruction(
                                    prog.currentManeuver,
                                    prog.distanceToManeuverMeters
                                )
                            )
                        }
                    },
                    onRecalculate = {
                        val origin = effectivePosition ?: route.points.firstOrNull()
                        val dest = route.points.lastOrNull()
                        if (origin != null && dest != null) {
                            scope.launch {
                                isCalculatingRoute = true
                                val recalculated = OpenRoutingClient.calculateRoadRoute(
                                    origin = origin,
                                    destination = dest,
                                    destinationName = route.title.removePrefix("Ruta a ").removePrefix("Ruta Directa (Offline) a "),
                                    viaPoints = route.waypoints,
                                    profile = selectedProfile,
                                    options = routeOptions
                                )
                                NavigationSessionRepository.startCalculatedRoute(
                                    result = recalculated,
                                    waypoints = route.waypoints,
                                    autoStart = (navigationState == NavigationRunState.NAVIGATING)
                                )
                                isCalculatingRoute = false
                            }
                        }
                    }
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = palette.hudOverlay),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Mapa Libre",
                                color = palette.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Busca un destino arriba o abre un track GPX en Rutas.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onOpenRoutes,
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "📂 Rutas",
                                color = palette.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            if (trafficLayerEnabled || isAutoRecalculating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (trafficLayerEnabled) {
                        Surface(
                            color = palette.hudOverlay,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.border),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "🚦 Tráfico: 🟢 Fluido · 🟡 Denso · 🔴 Retención (${trafficIncidents.size})",
                                color = palette.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (isAutoRecalculating) {
                        Surface(
                            color = palette.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🔄 Recalculando en marcha...",
                                color = palette.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. BOTONERA LATERAL DERECHA (Zoom +/-, Rotación Norte/Rumbo, Tráfico y Voz)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GloveRoundButton(label = "+", palette = palette) {
                manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
                mapViewRef?.controller?.zoomIn()
            }
            GloveRoundButton(label = "−", palette = palette) {
                manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
                mapViewRef?.controller?.zoomOut()
            }
            GloveRoundButton(
                label = if (mapOrientationMode == MapOrientationMode.COURSE_UP) "🏍️" else "🧭",
                palette = palette,
                borderColor = palette.primary
            ) {
                val next = AppPreferencesManager.toggleMapOrientationMode()
                if (next == MapOrientationMode.NORTH_UP) {
                    mapViewRef?.mapOrientation = 0f
                }
            }
            GloveRoundButton(
                label = "🚦",
                palette = palette,
                borderColor = if (trafficLayerEnabled) palette.success else palette.border
            ) {
                AppPreferencesManager.setTrafficLayerEnabled(!trafficLayerEnabled)
            }
            GloveRoundButton(
                label = if (voiceEnabled) "🔊" else "🔇",
                palette = palette,
                borderColor = if (voiceEnabled) palette.primary else palette.danger
            ) {
                voiceEnabled = !voiceEnabled
                if (voiceEnabled) {
                    ttsService.speak("Indicaciones por voz activadas.")
                }
            }
        }

        // 4. PANEL INFERIOR: Velocímetro + Controles de Vista Previa + Centrar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val route = activeRoute
            if (route != null && route.isPreviewSimulation && route.points.size > 1) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = palette.hudOverlay),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, palette.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "👁️ Vista Previa (${(previewIndex * 100) / route.points.lastIndex.coerceAtLeast(1)}%)",
                            color = palette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { previewPlaying = !previewPlaying },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (previewPlaying) "⏸ Pausa" else "▶ Avanzar",
                                    color = palette.onPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    NavigationSessionRepository.startNavigatingRoute(
                                        SavedRouteEntry(
                                            id = route.id,
                                            title = route.title,
                                            distanceKm = 0.0,
                                            elevationGainM = 0.0,
                                            curveCount = 0,
                                            estimatedCurviness = "",
                                            format = "GPX",
                                            dateAdded = "",
                                            points = route.points,
                                            waypoints = route.waypoints
                                        ),
                                        previewSimulation = false
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "GPS Real",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (route != null) {
                Surface(
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (navigationState) {
                            NavigationRunState.NAVIGATING -> {
                                Button(
                                    onClick = { NavigationSessionRepository.pauseNavigation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("⏸ Pausar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Button(
                                    onClick = { NavigationSessionRepository.stopNavigation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.danger),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("⏹ Parar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            NavigationRunState.PAUSED -> {
                                Button(
                                    onClick = { NavigationSessionRepository.resumeNavigation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("▶ Reanudar", color = palette.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Button(
                                    onClick = { NavigationSessionRepository.stopNavigation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.danger),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("⏹ Parar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            else -> {
                                Button(
                                    onClick = {
                                        followGps = true
                                        NavigationSessionRepository.startNavigation()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(
                                        text = "▶ Iniciar navegación",
                                        color = palette.onPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, palette.border),
                    shadowElevation = 5.dp,
                    modifier = Modifier.weight(1.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${effectiveSpeedKmh.roundToInt()}",
                                color = palette.textPrimary,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 36.sp
                            )
                            Text(
                                text = "km/h",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (liveProgress != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.1f km", liveProgress.remainingDistanceMeters / 1000.0),
                                    color = palette.primary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${liveProgress.remainingMinutes} min restantes",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (effectivePosition != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Alt. ${effectivePosition.elevationMeters?.roundToInt() ?: 0} m",
                                    color = palette.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "±${effectivePosition.accuracyMeters?.roundToInt() ?: 0} m GPS",
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        followGps = true
                        manualZoomPauseUntilMs = 0L
                        val target = effectivePosition ?: activeRoute?.points?.firstOrNull()
                        if (target != null) {
                            mapViewRef?.controller?.animateTo(OsmGeoPoint(target.latitude, target.longitude))
                            val z = if (speedAutoZoom && navigationState == NavigationRunState.NAVIGATING) {
                                NavigationDisplayCalculator.computeTargetZoomForSpeed(effectiveSpeedKmh)
                            } else {
                                16.0
                            }
                            mapViewRef?.controller?.setZoom(z)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (followGps) palette.primary else palette.hudOverlay
                    ),
                    border = BorderStroke(1.5.dp, palette.primary),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .height(64.dp)
                        .weight(0.9f)
                ) {
                    Text(
                        text = if (followGps) "🎯 Siguiendo" else "🎯 Centrar",
                        color = if (followGps) palette.onPrimary else palette.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 5. MODAL DE BÚSQUEDA DE DESTINO (Nominatim + OSRM)
        if (showSearchSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xBB0F172A))
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, palette.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🗺️ Calcular Ruta en Carretera",
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "✕ Cerrar",
                                color = palette.danger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showSearchSheet = false }
                                    .padding(6.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                MotorcycleRoutingProfile.CURVY to "🏍️ Curvas",
                                MotorcycleRoutingProfile.EXTRA_CURVY_SCENIC to "🏔️ Puertos",
                                MotorcycleRoutingProfile.FAST_HIGHWAY to "⚡ Rápida"
                            ).forEach { (profile, label) ->
                                val selected = selectedProfile == profile
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable { selectedProfile = profile },
                                    color = if (selected) palette.primary else palette.surfaceAlt,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (selected) palette.primary else palette.border)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (selected) palette.onPrimary else palette.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Preferencias de vía: Autovía, Peajes, Ferri, Caminos
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val toggleItems = listOf(
                                Triple("🛣️ Autovía", routeOptions.allowHighways) {
                                    AppPreferencesManager.updateRouteOptions { copy(allowHighways = !allowHighways) }
                                },
                                Triple("💶 Peajes", routeOptions.allowTolls) {
                                    AppPreferencesManager.updateRouteOptions { copy(allowTolls = !allowTolls) }
                                },
                                Triple("⛴️ Ferri", routeOptions.allowFerries) {
                                    AppPreferencesManager.updateRouteOptions { copy(allowFerries = !allowFerries) }
                                },
                                Triple("🪨 Caminos", routeOptions.allowUnpavedTracks) {
                                    AppPreferencesManager.updateRouteOptions { copy(allowUnpavedTracks = !allowUnpavedTracks) }
                                }
                            )
                            toggleItems.forEach { (label, active, onToggle) ->
                                Surface(
                                    modifier = Modifier.clickable { onToggle() },
                                    color = if (active) palette.primary.copy(alpha = 0.16f) else palette.surfaceAlt,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (active) palette.primary else palette.border)
                                ) {
                                    Text(
                                        text = if (active) "✓ $label" else "✕ $label",
                                        color = if (active) palette.primary else palette.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Destinos Favoritos (Casa, Trabajo, Personalizados)
                        if (favoriteDestinations.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                favoriteDestinations.forEach { fav ->
                                    Surface(
                                        color = palette.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, palette.primary),
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                isCalculatingRoute = true
                                                val origin = realLocation
                                                    ?: mapViewRef?.mapCenter?.let {
                                                        GeoPoint(it.latitude, it.longitude)
                                                    }
                                                    ?: GeoPoint(40.4168, -3.7038)
                                                val calculated = OpenRoutingClient.calculateRoadRoute(
                                                    origin = origin,
                                                    destination = GeoPoint(fav.latitude, fav.longitude),
                                                    destinationName = fav.name,
                                                    profile = selectedProfile,
                                                    options = routeOptions
                                                )
                                                NavigationSessionRepository.startCalculatedRoute(calculated)
                                                isCalculatingRoute = false
                                                showSearchSheet = false
                                                followGps = false
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "${fav.category.icon} ${fav.name}",
                                            color = palette.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Ej: Morella, Navacerrada, Ronda...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = palette.textPrimary,
                                    unfocusedTextColor = palette.textPrimary,
                                    focusedBorderColor = palette.primary,
                                    unfocusedBorderColor = palette.border
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        scope.launch {
                                            isSearching = true
                                            searchError = null
                                            OpenRoutingClient.searchDestination(searchQuery)
                                                .onSuccess { searchResults = it }
                                                .onFailure { searchError = "Sin conexión o error de búsqueda (${it.message})" }
                                            isSearching = false
                                        }
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    scope.launch {
                                        isSearching = true
                                        searchError = null
                                        OpenRoutingClient.searchDestination(searchQuery)
                                            .onSuccess { searchResults = it }
                                            .onFailure { searchError = "Sin conexión o error de búsqueda (${it.message})" }
                                        isSearching = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("Ir", color = palette.onPrimary, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        if (isSearching || isCalculatingRoute) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = palette.primary, modifier = Modifier.size(22.dp))
                                Text(
                                    text = if (isCalculatingRoute) "Trazando ruta por carreteras (OSRM)..." else "Buscando en OpenStreetMap...",
                                    color = palette.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        searchError?.let {
                            Text(text = it, color = palette.danger, fontSize = 12.sp)
                        }

                        LazyColumn(
                            modifier = Modifier.height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { place ->
                                Surface(
                                    color = palette.surfaceAlt,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                isCalculatingRoute = true
                                                val origin = realLocation
                                                    ?: mapViewRef?.mapCenter?.let {
                                                        GeoPoint(it.latitude, it.longitude)
                                                    }
                                                    ?: GeoPoint(40.4168, -3.7038)

                                                val calculated = OpenRoutingClient.calculateRoadRoute(
                                                    origin = origin,
                                                    destination = place.location,
                                                    destinationName = place.shortName,
                                                    profile = selectedProfile,
                                                    options = routeOptions
                                                )
                                                NavigationSessionRepository.startCalculatedRoute(calculated)
                                                isCalculatingRoute = false
                                                showSearchSheet = false
                                                followGps = false
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "📍 ${place.shortName}",
                                            color = palette.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = place.displayName,
                                            color = palette.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FavoritePlaceCategory.entries.forEach { cat ->
                                                Surface(
                                                    color = palette.surface,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, palette.border),
                                                    modifier = Modifier.clickable {
                                                        val favId = when (cat) {
                                                            FavoritePlaceCategory.HOME -> "fav_home"
                                                            FavoritePlaceCategory.WORK -> "fav_work"
                                                            FavoritePlaceCategory.CUSTOM -> "fav_${System.currentTimeMillis()}"
                                                        }
                                                        AppPreferencesManager.saveFavoriteDestination(
                                                            FavoriteDestination(
                                                                id = favId,
                                                                name = if (cat == FavoritePlaceCategory.CUSTOM) place.shortName else cat.label,
                                                                category = cat,
                                                                address = place.displayName,
                                                                latitude = place.location.latitude,
                                                                longitude = place.location.longitude
                                                            )
                                                        )
                                                    }
                                                ) {
                                                    Text(
                                                        text = "+ ${cat.icon} ${cat.label}",
                                                        color = palette.primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnByTurnBannerCard(
    route: ActiveRouteSession,
    progress: LiveNavigationProgress?,
    hasGpsFix: Boolean,
    palette: NavigatorPalette,
    routeColor: RouteTrackColor,
    onSpeakNow: () -> Unit,
    onRecalculate: () -> Unit
) {
    val isOffRoute = progress?.isOffRoute == true
    val cardBorder = if (isOffRoute) palette.danger else routeColor.composeColor
    val offRouteContainer = if (palette.isDark) Color(0xF53B1922) else Color(0xF7FFF1F2)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOffRoute) offRouteContainer else palette.hudOverlay
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (progress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isOffRoute) palette.danger else routeColor.composeColor)
                            .clickable { onSpeakNow() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = progress.currentManeuver.type.symbol,
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDistanceMeters(progress.distanceToManeuverMeters),
                            color = if (isOffRoute) palette.danger else palette.primary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isOffRoute) {
                                "Fuera de ruta (${progress.offRouteMeters.roundToInt()} m). Dirígete hacia ${progress.currentManeuver.instruction}"
                            } else {
                                val road = progress.currentManeuver.roadName
                                if (road.isNotBlank()) "${progress.currentManeuver.instruction} · $road"
                                else progress.currentManeuver.instruction
                            },
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (progress.nextManeuverAfter != null || isOffRoute) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        progress.nextManeuverAfter?.let { nextM ->
                            Text(
                                text = "Después: ${nextM.type.symbol} ${nextM.instruction}",
                                color = palette.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isOffRoute) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRecalculate,
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🔄 Recalcular",
                                    color = palette.onPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            } else {
                val firstManeuver = route.maneuvers.getOrNull(1) ?: route.maneuvers.firstOrNull()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = firstManeuver?.type?.symbol ?: "🧭",
                        color = routeColor.composeColor,
                        fontSize = 28.sp
                    )
                    Column {
                        Text(
                            text = route.title,
                            color = palette.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!hasGpsFix) {
                                "Track GPX cargado (${route.points.size} pts). Esperando GPS 3D para guiar giro a giro..."
                            } else {
                                firstManeuver?.instruction ?: "Sigue el trazado en el mapa"
                            },
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GloveRoundButton(
    label: String,
    palette: NavigatorPalette,
    borderColor: Color = palette.border,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(54.dp)
            .clickable(onClick = onClick),
        color = palette.hudOverlay,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun applyMapVisualStyle(map: MapView, style: MapColorStyle) {
    val desiredSource = when (style) {
        MapColorStyle.OSM_CLASSIC, MapColorStyle.OSM_NIGHT -> TileSourceFactory.MAPNIK
        MapColorStyle.OSM_HOT -> TileSourceOsmHot
        MapColorStyle.CYCL_OSM -> TileSourceOsmDeRutas
        MapColorStyle.OPEN_TOPO -> TileSourceFactory.OpenTopo
    }
    if (map.tileProvider.tileSource.name() != desiredSource.name()) {
        map.tileProvider.clearTileCache()
        map.setTileSource(desiredSource)
        map.invalidate()
    }

    if (style == MapColorStyle.OSM_NIGHT) {
        // Matriz M3 pizarra azulada suave (invertido controlado sin monocromo verde 80s ni marrones)
        val nightMatrix = ColorMatrix(
            floatArrayOf(
                -0.84f, 0f, 0f, 0f, 224f,
                0f, -0.82f, 0f, 0f, 232f,
                0f, 0f, -0.74f, 0f, 245f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(nightMatrix))
    } else {
        map.overlayManager.tilesOverlay.setColorFilter(null)
    }
}

private fun formatDistanceMeters(meters: Double): String {
    return if (meters >= 1000.0) {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    } else {
        "${((meters.roundToInt() / 10) * 10).coerceAtLeast(10)} m"
    }
}

private fun computePreviewBearing(route: ActiveRouteSession?, idx: Int): Float {
    if (route == null || route.points.size < 2) return 0f
    val curr = route.points[idx.coerceIn(0, route.points.lastIndex)]
    val next = route.points[(idx + 1).coerceAtMost(route.points.lastIndex)]
    return GpxKmlParser.bearingDegrees(curr, next).toFloat()
}

private fun createBadgeDrawable(context: Context, text: String, bgColor: Int): BitmapDrawable {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

    paint.color = bgColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = if (text.length >= 3) 20f else 24f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(text, size / 2f, yPos, paint)

    return BitmapDrawable(context.resources, bmp)
}

private fun createMotoArrowDrawable(context: Context): BitmapDrawable {
    val size = 84
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(95, 37, 99, 235)
    }
    canvas.drawCircle(cx, cy, 38f, haloPaint)

    val path = Path().apply {
        moveTo(cx, 10f)
        lineTo(cx + 22f, size - 16f)
        lineTo(cx, size - 26f)
        lineTo(cx - 22f, size - 16f)
        close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#2563EB")
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, strokePaint)

    return BitmapDrawable(context.resources, bmp)
}
