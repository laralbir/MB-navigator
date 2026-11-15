package org.mbnavigator.android.core.settings

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.voice.VoiceVerbosityLevel

enum class AppThemeMode(val label: String, val icon: String) {
    SYSTEM_SENSOR("Sistema / Sensor", "🌓"),
    TWILIGHT_HOURS("Horas Crepúsculo", "🌅"),
    LIGHT("Claro", "☀️"),
    DARK("Oscuro", "🌙")
}

enum class SystemNavBarMode(val label: String, val description: String) {
    ALWAYS_VISIBLE("Barra fija visible", "Mantiene siempre visible la barra inferior de Android"),
    GESTURE_AUTO_HIDE("Ocultar (gestos)", "Oculta la barra inferior de Android y la muestra deslizando desde abajo")
}

data class RouteCalculationOptions(
    val allowHighways: Boolean = true,
    val allowTolls: Boolean = true,
    val allowFerries: Boolean = true,
    val allowUnpavedTracks: Boolean = false
)

enum class FavoritePlaceCategory(val label: String, val icon: String) {
    HOME("Casa", "🏠"),
    WORK("Trabajo", "💼"),
    CUSTOM("Personalizado", "⭐")
}

data class FavoriteDestination(
    val id: String,
    val name: String,
    val category: FavoritePlaceCategory,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

enum class MapOrientationMode(val label: String, val shortLabel: String, val description: String) {
    COURSE_UP("Dirección de marcha", "🏍️ Rumbo", "El mapa gira automáticamente siguiendo el sentido de la moto"),
    NORTH_UP("Norte arriba", "🧭 Norte", "El mapa permanece orientado siempre hacia el norte geográfico")
}

enum class MapColorStyle(val label: String, val shortLabel: String, val description: String) {
    OSM_CLASSIC("OSM Clásico", "🗺️ OSM", "OpenStreetMap detallado estándar a todo color"),
    OSM_HOT("Pastel Suave", "🎨 Pastel", "Estilo suave OpenStreetMap con colores amables y claros"),
    CYCL_OSM("Rutas y Puertos", "🏍️ Rutas", "Destaca carreteras secundarias, puertos y curvas de nivel"),
    OPEN_TOPO("Topográfico", "⛰️ Topo", "Relieve completo, cotas altimétricas y valles"),
    OSM_NIGHT("Nocturno Azul", "🌌 Nocturno", "Mapa en tono pizarra azulada suave para conducción nocturna")
}

enum class RouteTrackColor(
    val label: String,
    val mainHex: String,
    val borderHex: String,
    val composeColor: Color
) {
    OCEAN_BLUE("Azul Océano", "#23759B", "#00506E", Color(0xFF23759B)),
    TEAL_MINT("Verde Agua", "#0D9488", "#115E59", Color(0xFF0D9488)),
    LAVENDER_IRIS("Lavanda M3", "#7E57C2", "#4527A0", Color(0xFF7E57C2)),
    CORAL_ROSE("Coral Suave", "#E11D48", "#881337", Color(0xFFE11D48))
}

data class NavigatorPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val hudOverlay: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val success: Color,
    val danger: Color
)

object AppPreferencesManager {
    private const val PREFS_NAME = "mb_nav_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_MAP_STYLE = "map_color_style"
    private const val KEY_ROUTE_COLOR = "route_track_color"
    private const val KEY_WIFI_ONLY = "wifi_only_downloads"
    private const val KEY_SYS_NAV_BAR = "system_nav_bar_mode"
    private const val KEY_AUTO_NIGHT_MAP = "auto_night_map"
    private const val KEY_MAP_ORIENTATION = "map_orientation_mode"
    private const val KEY_SPEED_AUTO_ZOOM = "speed_auto_zoom_enabled"
    private const val KEY_TRAFFIC_LAYER = "traffic_layer_enabled"
    private const val KEY_AUTO_RECALC = "auto_recalc_off_route"
    private const val KEY_OPT_HIGHWAYS = "route_opt_highways"
    private const val KEY_OPT_TOLLS = "route_opt_tolls"
    private const val KEY_OPT_FERRIES = "route_opt_ferries"
    private const val KEY_OPT_TRACKS = "route_opt_tracks"
    private const val KEY_FAVORITE_DESTINATIONS = "favorite_destinations_json"
    private const val KEY_VOICE_GUIDANCE = "voice_guidance_enabled"
    private const val KEY_VOICE_VERBOSITY = "voice_verbosity_level"
    private const val KEY_VOICE_SPEECH_RATE = "voice_speech_rate"
    private const val KEY_VOICE_LANGUAGE_TAG = "voice_language_tag"
    private const val KEY_DGT_ALERTS_ENABLED = "dgt_alerts_enabled"
    private const val KEY_SPEED_CAMERA_ALERTS_ENABLED = "speed_camera_alerts_enabled"

    private var prefs: SharedPreferences? = null

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM_SENSOR)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _mapStyle = MutableStateFlow(MapColorStyle.OSM_CLASSIC)
    val mapStyle: StateFlow<MapColorStyle> = _mapStyle.asStateFlow()

    private val _routeColor = MutableStateFlow(RouteTrackColor.OCEAN_BLUE)
    val routeColor: StateFlow<RouteTrackColor> = _routeColor.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(true)
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    private val _systemNavBarMode = MutableStateFlow(SystemNavBarMode.ALWAYS_VISIBLE)
    val systemNavBarMode: StateFlow<SystemNavBarMode> = _systemNavBarMode.asStateFlow()

    private val _autoNightMapEnabled = MutableStateFlow(true)
    val autoNightMapEnabled: StateFlow<Boolean> = _autoNightMapEnabled.asStateFlow()

    private val _mapOrientationMode = MutableStateFlow(MapOrientationMode.COURSE_UP)
    val mapOrientationMode: StateFlow<MapOrientationMode> = _mapOrientationMode.asStateFlow()

    private val _speedAutoZoomEnabled = MutableStateFlow(true)
    val speedAutoZoomEnabled: StateFlow<Boolean> = _speedAutoZoomEnabled.asStateFlow()

    private val _trafficLayerEnabled = MutableStateFlow(true)
    val trafficLayerEnabled: StateFlow<Boolean> = _trafficLayerEnabled.asStateFlow()

    private val _autoRecalculateEnabled = MutableStateFlow(true)
    val autoRecalculateEnabled: StateFlow<Boolean> = _autoRecalculateEnabled.asStateFlow()

    private val _routeOptions = MutableStateFlow(RouteCalculationOptions())
    val routeOptions: StateFlow<RouteCalculationOptions> = _routeOptions.asStateFlow()

    private val _favoriteDestinations = MutableStateFlow<List<FavoriteDestination>>(emptyList())
    val favoriteDestinations: StateFlow<List<FavoriteDestination>> = _favoriteDestinations.asStateFlow()

    private val _voiceGuidanceEnabled = MutableStateFlow(true)
    val voiceGuidanceEnabled: StateFlow<Boolean> = _voiceGuidanceEnabled.asStateFlow()

    private val _voiceVerbosity = MutableStateFlow(VoiceVerbosityLevel.STANDARD)
    val voiceVerbosity: StateFlow<VoiceVerbosityLevel> = _voiceVerbosity.asStateFlow()

    private val _voiceSpeechRate = MutableStateFlow(1.0f)
    val voiceSpeechRate: StateFlow<Float> = _voiceSpeechRate.asStateFlow()

    private val _voiceLanguageTag = MutableStateFlow("es-ES")
    val voiceLanguageTag: StateFlow<String> = _voiceLanguageTag.asStateFlow()

    private val _dgtAlertsEnabled = MutableStateFlow(true)
    val dgtAlertsEnabled: StateFlow<Boolean> = _dgtAlertsEnabled.asStateFlow()

    private val _speedCameraAlertsEnabled = MutableStateFlow(true)
    val speedCameraAlertsEnabled: StateFlow<Boolean> = _speedCameraAlertsEnabled.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        _themeMode.value = runCatching {
            val saved = sp.getString(KEY_THEME, AppThemeMode.SYSTEM_SENSOR.name)!!
            if (saved == "AUTO") AppThemeMode.SYSTEM_SENSOR else AppThemeMode.valueOf(saved)
        }.getOrDefault(AppThemeMode.SYSTEM_SENSOR)

        _mapStyle.value = runCatching {
            MapColorStyle.valueOf(sp.getString(KEY_MAP_STYLE, MapColorStyle.OSM_CLASSIC.name)!!)
        }.getOrDefault(MapColorStyle.OSM_CLASSIC)

        _routeColor.value = runCatching {
            RouteTrackColor.valueOf(sp.getString(KEY_ROUTE_COLOR, RouteTrackColor.OCEAN_BLUE.name)!!)
        }.getOrDefault(RouteTrackColor.OCEAN_BLUE)

        _systemNavBarMode.value = runCatching {
            SystemNavBarMode.valueOf(sp.getString(KEY_SYS_NAV_BAR, SystemNavBarMode.ALWAYS_VISIBLE.name)!!)
        }.getOrDefault(SystemNavBarMode.ALWAYS_VISIBLE)

        _wifiOnlyDownloads.value = sp.getBoolean(KEY_WIFI_ONLY, true)
        _autoNightMapEnabled.value = sp.getBoolean(KEY_AUTO_NIGHT_MAP, true)

        _mapOrientationMode.value = runCatching {
            MapOrientationMode.valueOf(
                sp.getString(KEY_MAP_ORIENTATION, MapOrientationMode.COURSE_UP.name)!!
            )
        }.getOrDefault(MapOrientationMode.COURSE_UP)
        _speedAutoZoomEnabled.value = sp.getBoolean(KEY_SPEED_AUTO_ZOOM, true)
        _trafficLayerEnabled.value = sp.getBoolean(KEY_TRAFFIC_LAYER, true)
        _autoRecalculateEnabled.value = sp.getBoolean(KEY_AUTO_RECALC, true)
        _routeOptions.value = RouteCalculationOptions(
            allowHighways = sp.getBoolean(KEY_OPT_HIGHWAYS, true),
            allowTolls = sp.getBoolean(KEY_OPT_TOLLS, true),
            allowFerries = sp.getBoolean(KEY_OPT_FERRIES, true),
            allowUnpavedTracks = sp.getBoolean(KEY_OPT_TRACKS, false)
        )
        _favoriteDestinations.value = parseFavoritesJson(sp.getString(KEY_FAVORITE_DESTINATIONS, null))
        _voiceGuidanceEnabled.value = sp.getBoolean(KEY_VOICE_GUIDANCE, true)
        _voiceVerbosity.value = runCatching {
            VoiceVerbosityLevel.valueOf(sp.getString(KEY_VOICE_VERBOSITY, VoiceVerbosityLevel.STANDARD.name)!!)
        }.getOrDefault(VoiceVerbosityLevel.STANDARD)
        _voiceSpeechRate.value = sp.getFloat(KEY_VOICE_SPEECH_RATE, 1.0f)
        _voiceLanguageTag.value = sp.getString(KEY_VOICE_LANGUAGE_TAG, "es-ES") ?: "es-ES"
        _dgtAlertsEnabled.value = sp.getBoolean(KEY_DGT_ALERTS_ENABLED, true)
        _speedCameraAlertsEnabled.value = sp.getBoolean(KEY_SPEED_CAMERA_ALERTS_ENABLED, true)
    }

    fun setVoiceGuidanceEnabled(enabled: Boolean) {
        _voiceGuidanceEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_VOICE_GUIDANCE, enabled)?.apply()
    }

    fun setVoiceVerbosity(level: VoiceVerbosityLevel) {
        _voiceVerbosity.value = level
        prefs?.edit()?.putString(KEY_VOICE_VERBOSITY, level.name)?.apply()
    }

    fun setVoiceSpeechRate(rate: Float) {
        _voiceSpeechRate.value = rate
        prefs?.edit()?.putFloat(KEY_VOICE_SPEECH_RATE, rate)?.apply()
    }

    fun setVoiceLanguageTag(tag: String) {
        _voiceLanguageTag.value = tag
        prefs?.edit()?.putString(KEY_VOICE_LANGUAGE_TAG, tag)?.apply()
    }

    fun setDgtAlertsEnabled(enabled: Boolean) {
        _dgtAlertsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_DGT_ALERTS_ENABLED, enabled)?.apply()
    }

    fun setSpeedCameraAlertsEnabled(enabled: Boolean) {
        _speedCameraAlertsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SPEED_CAMERA_ALERTS_ENABLED, enabled)?.apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }

    fun setMapStyle(style: MapColorStyle) {
        _mapStyle.value = style
        prefs?.edit()?.putString(KEY_MAP_STYLE, style.name)?.apply()
    }

    fun cycleMapStyle(): MapColorStyle {
        val all = MapColorStyle.entries
        val next = all[(_mapStyle.value.ordinal + 1) % all.size]
        setMapStyle(next)
        return next
    }

    fun setRouteColor(color: RouteTrackColor) {
        _routeColor.value = color
        prefs?.edit()?.putString(KEY_ROUTE_COLOR, color.name)?.apply()
    }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        _wifiOnlyDownloads.value = wifiOnly
        prefs?.edit()?.putBoolean(KEY_WIFI_ONLY, wifiOnly)?.apply()
    }

    fun setSystemNavBarMode(mode: SystemNavBarMode) {
        _systemNavBarMode.value = mode
        prefs?.edit()?.putString(KEY_SYS_NAV_BAR, mode.name)?.apply()
    }

    fun setSpeedAutoZoomEnabled(enabled: Boolean) {
        _speedAutoZoomEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SPEED_AUTO_ZOOM, enabled)?.apply()
    }

    fun setTrafficLayerEnabled(enabled: Boolean) {
        _trafficLayerEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_TRAFFIC_LAYER, enabled)?.apply()
    }

    fun setAutoRecalculateEnabled(enabled: Boolean) {
        _autoRecalculateEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_RECALC, enabled)?.apply()
    }

    fun updateRouteOptions(transform: RouteCalculationOptions.() -> RouteCalculationOptions) {
        val next = _routeOptions.value.transform()
        _routeOptions.value = next
        prefs?.edit()
            ?.putBoolean(KEY_OPT_HIGHWAYS, next.allowHighways)
            ?.putBoolean(KEY_OPT_TOLLS, next.allowTolls)
            ?.putBoolean(KEY_OPT_FERRIES, next.allowFerries)
            ?.putBoolean(KEY_OPT_TRACKS, next.allowUnpavedTracks)
            ?.apply()
    }

    fun saveFavoriteDestination(entry: FavoriteDestination) {
        val current = _favoriteDestinations.value.toMutableList()
        if (entry.category == FavoritePlaceCategory.HOME || entry.category == FavoritePlaceCategory.WORK) {
            current.removeAll { it.category == entry.category }
        } else {
            current.removeAll { it.id == entry.id }
        }
        val updated = when (entry.category) {
            FavoritePlaceCategory.HOME -> listOf(entry) + current
            FavoritePlaceCategory.WORK -> {
                val home = current.filter { it.category == FavoritePlaceCategory.HOME }
                val rest = current.filter { it.category != FavoritePlaceCategory.HOME }
                home + listOf(entry) + rest
            }
            FavoritePlaceCategory.CUSTOM -> current + entry
        }
        _favoriteDestinations.value = updated
        persistFavorites(updated)
    }

    fun removeFavoriteDestination(id: String) {
        val updated = _favoriteDestinations.value.filterNot { it.id == id }
        _favoriteDestinations.value = updated
        persistFavorites(updated)
    }

    private fun persistFavorites(items: List<FavoriteDestination>) {
        val serialized = items.joinToString("\n") { fav ->
            listOf(
                fav.id.replace("|", "_"),
                fav.category.name,
                fav.name.replace("|", " ").replace("\n", " "),
                fav.address.replace("|", " ").replace("\n", " "),
                fav.latitude.toString(),
                fav.longitude.toString()
            ).joinToString("|")
        }
        prefs?.edit()?.putString(KEY_FAVORITE_DESTINATIONS, serialized)?.apply()
    }

    internal fun serializeFavorites(items: List<FavoriteDestination>): String {
        return items.joinToString("\n") { fav ->
            listOf(
                fav.id.replace("|", "_"),
                fav.category.name,
                fav.name.replace("|", " ").replace("\n", " "),
                fav.address.replace("|", " ").replace("\n", " "),
                fav.latitude.toString(),
                fav.longitude.toString()
            ).joinToString("|")
        }
    }

    internal fun parseFavoritesJson(raw: String?): List<FavoriteDestination> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 6) return@mapNotNull null
                val lat = parts[4].toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[5].toDoubleOrNull() ?: return@mapNotNull null
                val cat = runCatching { FavoritePlaceCategory.valueOf(parts[1]) }
                    .getOrDefault(FavoritePlaceCategory.CUSTOM)
                FavoriteDestination(
                    id = parts[0],
                    name = parts[2],
                    category = cat,
                    address = parts[3],
                    latitude = lat,
                    longitude = lon
                )
            }
            .toList()
    }

    fun setAutoNightMapEnabled(enabled: Boolean) {
        _autoNightMapEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_NIGHT_MAP, enabled)?.apply()
    }

    fun setMapOrientationMode(mode: MapOrientationMode) {
        _mapOrientationMode.value = mode
        prefs?.edit()?.putString(KEY_MAP_ORIENTATION, mode.name)?.apply()
    }

    fun toggleMapOrientationMode(): MapOrientationMode {
        val next = if (_mapOrientationMode.value == MapOrientationMode.COURSE_UP) {
            MapOrientationMode.NORTH_UP
        } else {
            MapOrientationMode.COURSE_UP
        }
        setMapOrientationMode(next)
        return next
    }

    /**
     * Resuelve si el modo noche está activo teniendo en cuenta la preferencia elegida,
     * el estado nocturno del sistema / Android Auto (UiModeManager) o el cálculo crepuscular GPS.
     */
    fun isDarkActive(
        mode: AppThemeMode,
        systemIsDark: Boolean,
        context: Context? = null,
        location: GeoPoint? = null
    ): Boolean {
        return when (mode) {
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
            AppThemeMode.TWILIGHT_HOURS -> TwilightCalculator.isNightTime(location)
            AppThemeMode.SYSTEM_SENSOR -> {
                if (systemIsDark) {
                    true
                } else if (context != null) {
                    val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val uiManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
                    val isCarNight = uiManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR &&
                        uiManager.nightMode == UiModeManager.MODE_NIGHT_YES
                    uiMode == Configuration.UI_MODE_NIGHT_YES || isCarNight
                } else {
                    false
                }
            }
        }
    }
}

@Composable
fun rememberIsDarkModeActive(location: GeoPoint? = null): Boolean {
    val context = LocalContext.current
    val mode by AppPreferencesManager.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    return AppPreferencesManager.isDarkActive(
        mode = mode,
        systemIsDark = systemDark,
        context = context,
        location = location
    )
}

@Composable
fun rememberEffectiveMapStyle(location: GeoPoint? = null): MapColorStyle {
    val baseStyle by AppPreferencesManager.mapStyle.collectAsState()
    val autoNightMap by AppPreferencesManager.autoNightMapEnabled.collectAsState()
    val isDark = rememberIsDarkModeActive(location)
    return if (autoNightMap && isDark && baseStyle != MapColorStyle.OSM_NIGHT) {
        MapColorStyle.OSM_NIGHT
    } else {
        baseStyle
    }
}

/**
 * Paleta oficial siguiendo guías tonales de Material Design 3 (m3.material.io):
 * tonos azul océano/cielo suaves, superficies tonales confortables y cero marrón dominante.
 */
@Composable
fun rememberNavigatorPalette(location: GeoPoint? = null): NavigatorPalette {
    val isDark = rememberIsDarkModeActive(location)

    return if (isDark) {
        NavigatorPalette(
            isDark = true,
            bg = Color(0xFF0E141B),
            surface = Color(0xFF17202B),
            surfaceAlt = Color(0xFF212D3C),
            hudOverlay = Color(0xF217202B),
            border = Color(0xFF2E3C4E),
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFFAAB8C8),
            primary = Color(0xFF5FA8FF),
            onPrimary = Color(0xFF072342),
            secondary = Color(0xFF38BDF8),
            success = Color(0xFF34D399),
            danger = Color(0xFFFF7B7B)
        )
    } else {
        NavigatorPalette(
            isDark = false,
            bg = Color(0xFFF5F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFEEF2F8),
            hudOverlay = Color(0xFAFFFFFF),
            border = Color(0xFFE2E8F0),
            textPrimary = Color(0xFF111827),
            textSecondary = Color(0xFF64748B),
            primary = Color(0xFF1A56DB),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF0EA5E9),
            success = Color(0xFF10B981),
            danger = Color(0xFFEF4444)
        )
    }
}
