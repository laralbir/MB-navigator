package org.mbnavigator.android.ui.navigation

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.routing.SavedRouteEntry
import org.mbnavigator.android.core.routing.TurnByTurnEngine
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.MapColorStyle
import org.mbnavigator.android.core.settings.NavigatorPalette
import org.mbnavigator.android.core.settings.RouteTrackColor
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

private val TileSourceCartoVoyager by lazy {
    XYTileSource(
        "CartoVoyager", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    )
}

private val TileSourceCartoPositron by lazy {
    XYTileSource(
        "CartoPositron", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/"
        )
    )
}

private val TileSourceCartoDark by lazy {
    XYTileSource(
        "CartoDarkMatter", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/"
        )
    )
}

@Composable
fun MotorcycleNavigationScreen(
    onOpenGpsDiagnostics: () -> Unit = {},
    onOpenRoutes: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = rememberNavigatorPalette()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val locationTracker = remember { RealLocationTracker.getInstance(context) }
    val ttsService = remember { AndroidTextToSpeechService(context) }

    val realLocation by locationTracker.currentLocation.collectAsState()
    val hasGpsFix by locationTracker.hasGpsFix.collectAsState()
    val gnssSummary by locationTracker.gnssSummary.collectAsState()
    val activeRoute by NavigationSessionRepository.activeRoute.collectAsState()
    val mapStyle by AppPreferencesManager.mapStyle.collectAsState()
    val routeColor by AppPreferencesManager.routeColor.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(locationTracker.hasPermission()) }
    var followGps by remember { mutableStateOf(true) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var selectedProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }

    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
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

    LaunchedEffect(activeRoute?.id, activeRoute?.isPreviewSimulation, previewPlaying) {
        val route = activeRoute
        if (route != null && route.isPreviewSimulation && previewPlaying && route.points.size > 1) {
            while (previewPlaying) {
                delay(750L)
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

    LaunchedEffect(liveProgress?.currentManeuver?.index, liveProgress?.distanceToManeuverMeters, voiceEnabled) {
        val progress = liveProgress ?: return@LaunchedEffect
        if (!voiceEnabled || activeRoute == null) return@LaunchedEffect

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

    // Dibujar / actualizar capas del mapa OSMDroid (estilo de mapa, trazado colorido, waypoints y cursor)
    LaunchedEffect(mapViewRef, activeRoute, effectivePosition, followGps, mapStyle, routeColor) {
        val map = mapViewRef ?: return@LaunchedEffect
        applyMapVisualStyle(map, mapStyle)

        val routeKey = activeRoute?.let { "${it.id}_${it.points.size}_${routeColor.name}" }
        val routeChanged = routeKey != lastRenderedRouteKey

        if (routeChanged) {
            lastRenderedRouteKey = routeKey
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
                    followGps = route.isPreviewSimulation
                } else {
                    map.controller.setCenter(osmPoints.first())
                    map.controller.setZoom(15.0)
                }
            }
        }

        val cursorTag = "MOTO_CURSOR"
        val existingCursor = map.overlays.filterIsInstance<Marker>().find { it.id == cursorTag }
        if (effectivePosition != null) {
            val posOsm = OsmGeoPoint(effectivePosition.latitude, effectivePosition.longitude)
            val bearing = if (activeRoute?.isPreviewSimulation == true) {
                computePreviewBearing(activeRoute, previewIndex)
            } else {
                effectivePosition.bearingDegrees ?: 0f
            }

            val cursorMarker = existingCursor ?: Marker(map).apply {
                id = cursorTag
                title = "Tu moto"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createMotoArrowDrawable(context)
                map.overlays.add(this)
            }
            cursorMarker.position = posOsm
            cursorMarker.rotation = -bearing

            if (followGps && !routeChanged) {
                map.controller.animateTo(posOsm)
                if (map.zoomLevelDouble < 14.0) {
                    map.controller.setZoom(15.5)
                }
            }
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
                MapView(ctx).apply {
                    setTileSource(TileSourceCartoVoyager)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(6.5)
                    controller.setCenter(OsmGeoPoint(40.4168, -3.7038))
                    applyMapVisualStyle(this, mapStyle)

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (event != null && (event.x != 0 || event.y != 0)) {
                                followGps = false
                            }
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean = false
                    })
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
                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .clickable { onOpenGpsDiagnostics() },
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (hasGpsFix) palette.success else palette.secondary
                    )
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
                            text = mapStyle.shortLabel,
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
                                    destinationName = route.title,
                                    viaPoints = route.waypoints,
                                    profile = selectedProfile
                                )
                                NavigationSessionRepository.startCalculatedRoute(recalculated, route.waypoints)
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
        }

        // 3. BOTONERA LATERAL DERECHA (Zoom +/- + Voz)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GloveRoundButton(label = "+", palette = palette) {
                mapViewRef?.controller?.zoomIn()
            }
            GloveRoundButton(label = "−", palette = palette) {
                mapViewRef?.controller?.zoomOut()
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    color = palette.hudOverlay,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, palette.border),
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
                        val target = effectivePosition ?: activeRoute?.points?.firstOrNull()
                        if (target != null) {
                            mapViewRef?.controller?.animateTo(OsmGeoPoint(target.latitude, target.longitude))
                            mapViewRef?.controller?.setZoom(16.0)
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
                                                    profile = selectedProfile
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

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOffRoute) palette.danger.copy(alpha = 0.18f) else palette.hudOverlay
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
                            color = if (isOffRoute) palette.secondary else routeColor.composeColor,
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
                                colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "🔄 Recalcular",
                                    color = Color.White,
                                    fontSize = 11.sp,
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
    map.overlayManager.tilesOverlay.setColorFilter(null)
    val desiredSource = when (style) {
        MapColorStyle.CARTO_VOYAGER -> TileSourceCartoVoyager
        MapColorStyle.OSM_CLASSIC -> TileSourceFactory.MAPNIK
        MapColorStyle.OPEN_TOPO -> TileSourceFactory.OpenTopo
        MapColorStyle.CARTO_POSITRON -> TileSourceCartoPositron
        MapColorStyle.CARTO_DARK -> TileSourceCartoDark
    }
    if (map.tileProvider.tileSource != desiredSource) {
        map.setTileSource(desiredSource)
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
