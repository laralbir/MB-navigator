package org.mbnavigator.android.auto

import android.animation.ValueAnimator
import android.app.Presentation
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.ActiveRouteSession
import org.mbnavigator.android.core.routing.HazardCategory
import org.mbnavigator.android.core.routing.NavigationDisplayCalculator
import org.mbnavigator.android.core.routing.NavigationRunState
import org.mbnavigator.android.core.routing.TrafficDensityClient
import org.mbnavigator.android.core.routing.TrafficIncidentPoint
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.MapColorStyle
import org.mbnavigator.android.core.settings.MapOrientationMode
import org.mbnavigator.android.core.settings.RouteTrackColor
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * Renderizador de mapa OpenStreetMap sobre el Surface de Android Auto mediante
 * VirtualDisplay + Presentation para proyección completa en pantalla del vehículo/moto.
 */
class CarSurfaceMapRenderer(
    private val carContext: CarContext
) : SurfaceCallback, DefaultLifecycleObserver {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MapPresentation? = null
    private var mapView: MapView? = null

    private var followGps: Boolean = true
    private var manualZoomPauseUntilMs: Long = 0L
    private var stableHeadingDegrees: Float = 0f
    private var orientationAnimator: ValueAnimator? = null
    private var lastRenderedRouteKey: String? = null
    private var lastAppliedTrafficKey: String? = null
    private var lastFittedRouteId: String? = null
    private var lastCenteredOsm: OsmGeoPoint? = null
    private var visibleArea: Rect? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    // Estado más reciente enviado desde MBAutoNavigationScreen
    var activeRoute: ActiveRouteSession? = null
    var navigationState: NavigationRunState = NavigationRunState.IDLE
    var effectivePosition: GeoPoint? = null
    var effectiveSpeedKmh: Float = 0f
    var previewBearingDegrees: Float = 0f
    var incidents: List<TrafficIncidentPoint> = emptyList()

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        releaseSurface()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        mainHandler.post {
            releaseSurface()
            val surface = surfaceContainer.surface ?: return@post
            val width = surfaceContainer.width.coerceAtLeast(320)
            val height = surfaceContainer.height.coerceAtLeast(240)
            val dpi = surfaceContainer.dpi.coerceAtLeast(120)
            surfaceWidth = width
            surfaceHeight = height

            val vd = displayManager.createVirtualDisplay(
                "MBNavigatorAutoSurface",
                width,
                height,
                dpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )
            virtualDisplay = vd
            val pres = MapPresentation(carContext, vd)
            presentation = pres
            pres.show()
            mapView = pres.mapView
            lastRenderedRouteKey = null
            lastAppliedTrafficKey = null
            updateMapFrame()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mainHandler.post {
            releaseSurface()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        mainHandler.post {
            applyCenterOffset()
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        if (this.visibleArea == null) {
            this.visibleArea = stableArea
            mainHandler.post { applyCenterOffset() }
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        mainHandler.post {
            val map = mapView ?: return@post
            followGps = false
            manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
            map.scrollBy(distanceX.toInt(), distanceY.toInt())
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        mainHandler.post {
            val map = mapView ?: return@post
            manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
            if (scaleFactor > 1.03f) {
                map.controller.zoomIn()
            } else if (scaleFactor < 0.97f) {
                map.controller.zoomOut()
            }
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        // El desplazamiento continuo de onScroll cubre gestos suaves en pantalla de coche
    }

    fun zoomIn() {
        mainHandler.post {
            manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
            mapView?.controller?.zoomIn()
        }
    }

    fun zoomOut() {
        mainHandler.post {
            manualZoomPauseUntilMs = System.currentTimeMillis() + 12_000L
            mapView?.controller?.zoomOut()
        }
    }

    fun recenter() {
        mainHandler.post {
            followGps = true
            manualZoomPauseUntilMs = 0L
            lastCenteredOsm = null
            updateMapFrame()
        }
    }

    fun updateMapFrame() {
        val map = mapView ?: return
        applyCarMapStyle(map)

        val trafficLayerEnabled = AppPreferencesManager.trafficLayerEnabled.value
        val dgtAlertsEnabled = AppPreferencesManager.dgtAlertsEnabled.value
        val speedCameraAlertsEnabled = AppPreferencesManager.speedCameraAlertsEnabled.value
        val routeColor = AppPreferencesManager.routeColor.value
        val mapOrientationMode = AppPreferencesManager.mapOrientationMode.value
        val speedAutoZoom = AppPreferencesManager.speedAutoZoomEnabled.value

        val trafficKey = "${trafficLayerEnabled}_${dgtAlertsEnabled}_${speedCameraAlertsEnabled}_${incidents.size}_${incidents.firstOrNull()?.id.orEmpty()}"
        val routeKey = activeRoute?.let { "${it.id}_${it.points.size}_${routeColor.name}" }
        val overlaysChanged = (routeKey != lastRenderedRouteKey) || (trafficKey != lastAppliedTrafficKey)

        if (overlaysChanged) {
            val isNewRouteId = routeKey != null && activeRoute?.id != lastFittedRouteId
            lastRenderedRouteKey = routeKey
            lastAppliedTrafficKey = trafficKey
            map.overlays.clear()

            val route = activeRoute
            if (route != null && route.points.isNotEmpty()) {
                val osmPoints = route.points.map { OsmGeoPoint(it.latitude, it.longitude) }
                val borderLine = Polyline(map).apply {
                    setPoints(osmPoints)
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = android.graphics.Color.parseColor(routeColor.borderHex)
                    outlinePaint.strokeWidth = 22f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                val mainLine = Polyline(map).apply {
                    setPoints(osmPoints)
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = android.graphics.Color.parseColor(routeColor.mainHex)
                    outlinePaint.strokeWidth = 13f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                }
                map.overlays.add(borderLine)
                map.overlays.add(mainLine)

                if (trafficLayerEnabled) {
                    val segs = TrafficDensityClient.buildRouteTrafficSegments(route.points, incidents)
                    segs.forEach { seg ->
                        val tLine = Polyline(map).apply {
                            setPoints(seg.points.map { OsmGeoPoint(it.latitude, it.longitude) })
                            outlinePaint.isAntiAlias = true
                            outlinePaint.color = android.graphics.Color.parseColor(seg.level.colorHex)
                            outlinePaint.strokeWidth = 7f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        map.overlays.add(tLine)
                    }
                }

                map.overlays.add(
                    Marker(map).apply {
                        position = osmPoints.first()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = CarIconFactory.createBadgeDrawable(carContext, "IN", android.graphics.Color.parseColor("#0284C7"))
                    }
                )
                route.waypoints.forEachIndexed { idx, wp ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = OsmGeoPoint(wp.point.latitude, wp.point.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = CarIconFactory.createBadgeDrawable(
                                carContext,
                                "${idx + 1}",
                                android.graphics.Color.parseColor(if (wp.isMandatoryPassThrough) "#7E57C2" else "#64748B")
                            )
                        }
                    )
                }
                map.overlays.add(
                    Marker(map).apply {
                        position = osmPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = CarIconFactory.createBadgeDrawable(carContext, "FIN", android.graphics.Color.parseColor("#DC2626"))
                    }
                )

                if (isNewRouteId) {
                    lastFittedRouteId = route.id
                    lastCenteredOsm = null
                    if (osmPoints.size >= 2) {
                        val bbox = BoundingBox.fromGeoPoints(osmPoints)
                        map.post {
                            runCatching { map.zoomToBoundingBox(bbox, false, 90) }
                        }
                        followGps = route.isPreviewSimulation || navigationState == NavigationRunState.NAVIGATING
                    }
                }
            }

            if (incidents.isNotEmpty()) {
                incidents.take(40).forEach { inc ->
                    val show = when {
                        inc.category == HazardCategory.SPEED_CAMERA -> speedCameraAlertsEnabled
                        inc.category.isCriticalSafetyHazard -> trafficLayerEnabled || dgtAlertsEnabled
                        else -> trafficLayerEnabled
                    }
                    if (show) {
                        val code = when (inc.category) {
                            HazardCategory.SPEED_CAMERA -> "R"
                            HazardCategory.ACCIDENT -> "ACC"
                            HazardCategory.BROKEN_DOWN_VEHICLE -> "V16"
                            HazardCategory.ROAD_CLOSURE -> "X"
                            else -> "!"
                        }
                        map.overlays.add(
                            Marker(map).apply {
                                position = OsmGeoPoint(inc.point.latitude, inc.point.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = CarIconFactory.createBadgeDrawable(
                                    carContext,
                                    code,
                                    android.graphics.Color.parseColor(inc.category.colorHex)
                                )
                            }
                        )
                    }
                }
            }
        }

        val isSimulation = activeRoute?.isPreviewSimulation == true
        val rawBearing = if (isSimulation) {
            previewBearingDegrees
        } else {
            effectivePosition?.bearingDegrees ?: stableHeadingDegrees
        }
        if (NavigationDisplayCalculator.shouldUpdateHeadingBearing(effectiveSpeedKmh, isSimulation)) {
            val delta = NavigationDisplayCalculator.shortestAngleDeltaDegrees(stableHeadingDegrees, rawBearing)
            if (kotlin.math.abs(delta) >= 3.5f) {
                stableHeadingDegrees = NavigationDisplayCalculator.normalizeAngle360(stableHeadingDegrees + delta)
            }
        }

        val pos = effectivePosition
        if (pos != null) {
            val posOsm = OsmGeoPoint(pos.latitude, pos.longitude)
            val cursorTag = "MOTO_AUTO_CURSOR"
            val cursor = map.overlays.filterIsInstance<Marker>().find { it.id == cursorTag }
                ?: Marker(map).apply {
                    id = cursorTag
                    isFlat = true
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = CarIconFactory.createMotoArrowDrawable(carContext)
                    map.overlays.add(this)
                }
            cursor.position = posOsm
            cursor.rotation = -stableHeadingDegrees

            if (followGps) {
                val targetAngle = if (mapOrientationMode == MapOrientationMode.COURSE_UP) {
                    -stableHeadingDegrees
                } else {
                    0f
                }
                val angleDelta = NavigationDisplayCalculator.shortestAngleDeltaDegrees(map.mapOrientation, targetAngle)
                if (kotlin.math.abs(angleDelta) >= 2.5f) {
                    orientationAnimator?.cancel()
                    val startAngle = map.mapOrientation
                    orientationAnimator = ValueAnimator.ofFloat(startAngle, startAngle + angleDelta).apply {
                        duration = 380L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            map.mapOrientation = anim.animatedValue as Float
                            map.invalidate()
                        }
                        start()
                    }
                }

                val nowMs = System.currentTimeMillis()
                val desiredZoom = if (speedAutoZoom &&
                    navigationState == NavigationRunState.NAVIGATING &&
                    nowMs > manualZoomPauseUntilMs
                ) {
                    val targetZ = NavigationDisplayCalculator.computeTargetZoomForSpeed(effectiveSpeedKmh)
                    if (NavigationDisplayCalculator.shouldUpdateAutoZoom(map.zoomLevelDouble, targetZ)) targetZ else null
                } else if (map.zoomLevelDouble < 14.0 && !overlaysChanged) {
                    15.5
                } else {
                    null
                }

                val movedMeaningfully = lastCenteredOsm == null ||
                    lastCenteredOsm!!.distanceToAsDouble(posOsm) >= 1.5

                if (desiredZoom != null) {
                    map.controller.animateTo(posOsm, desiredZoom, 450L)
                    lastCenteredOsm = posOsm
                } else if (movedMeaningfully) {
                    map.controller.animateTo(posOsm, map.zoomLevelDouble, 420L)
                    lastCenteredOsm = posOsm
                }
            }
        }
        map.invalidate()
    }

    private fun applyCenterOffset() {
        val map = mapView ?: return
        val rect = visibleArea ?: return
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        val offsetX = ((rect.left + rect.right) / 2f - surfaceWidth / 2f).toInt()
        val offsetY = ((rect.top + rect.bottom) / 2f - surfaceHeight / 2f).toInt()
        map.setMapCenterOffset(offsetX, offsetY)
    }

    private fun applyCarMapStyle(map: MapView) {
        val baseStyle = AppPreferencesManager.mapStyle.value
        val autoNight = AppPreferencesManager.autoNightMapEnabled.value
        val carIsDark = carContext.isDarkMode || AppPreferencesManager.isDarkActive(
            AppPreferencesManager.themeMode.value,
            systemIsDark = false,
            context = carContext,
            location = effectivePosition
        )
        val style = if (autoNight && carIsDark && baseStyle != MapColorStyle.OSM_NIGHT) {
            MapColorStyle.OSM_NIGHT
        } else {
            baseStyle
        }

        val currentTag = map.tag as? String
        if (currentTag == style.name) return
        map.tag = style.name

        when (style) {
            MapColorStyle.OSM_CLASSIC -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }
            MapColorStyle.OSM_HOT -> {
                map.setTileSource(TileSourceFactory.OpenTopo)
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }
            MapColorStyle.CYCL_OSM -> {
                map.setTileSource(
                    XYTileSource(
                        "OSM_DE", 0, 19, 256, ".png",
                        arrayOf("https://tile.openstreetmap.de/"),
                        "© OpenStreetMap"
                    )
                )
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }
            MapColorStyle.OPEN_TOPO -> {
                map.setTileSource(TileSourceFactory.OpenTopo)
                map.overlayManager.tilesOverlay.setColorFilter(null)
            }
            MapColorStyle.OSM_NIGHT -> {
                map.setTileSource(TileSourceFactory.MAPNIK)
                val nightMatrix = ColorMatrix(
                    floatArrayOf(
                        -0.78f, 0f, 0f, 0f, 215f,
                        0f, -0.74f, 0f, 0f, 222f,
                        0f, 0f, -0.62f, 0f, 238f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                map.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(nightMatrix))
            }
        }
    }

    private fun releaseSurface() {
        orientationAnimator?.cancel()
        orientationAnimator = null
        runCatching { mapView?.onPause() }
        mapView = null
        runCatching { presentation?.dismiss() }
        presentation = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
    }

    private class MapPresentation(
        outerContext: Context,
        display: VirtualDisplay
    ) : Presentation(outerContext, display.display) {
        lateinit var mapView: MapView
            private set

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            mapView = MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(15.0)
                controller.setCenter(OsmGeoPoint(40.4168, -3.7038))
                onResume()
            }
            setContentView(mapView)
        }
    }
}
