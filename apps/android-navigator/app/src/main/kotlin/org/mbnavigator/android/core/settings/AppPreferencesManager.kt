package org.mbnavigator.android.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val label: String, val icon: String) {
    AUTO("Automático", "🌓"),
    LIGHT("Claro", "☀️"),
    DARK("Oscuro", "🌙")
}

enum class MapColorStyle(val label: String, val shortLabel: String, val description: String) {
    OSM_CLASSIC("OSM Clásico", "🗺️ OSM", "OpenStreetMap detallado estándar a todo color"),
    OSM_HOT("Pastel Suave", "🎨 Pastel", "Estilo suave OpenStreetMap con colores amables y claros"),
    CYCL_OSM("Rutas y Puertos", "🏍️ Rutas", "Destaca carreteras secundarias, puertos y curvas de nivel"),
    OPEN_TOPO("Topográfico", "⛰️ Topo", "Relieve completo, cotas altimétricas y valles"),
    OSM_NIGHT("Nocturno Azul", "🌌 Nocturno", "Mapa en tono pizarra azulado suave para conducción nocturna")
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

    private var prefs: SharedPreferences? = null

    private val _themeMode = MutableStateFlow(AppThemeMode.AUTO)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _mapStyle = MutableStateFlow(MapColorStyle.OSM_CLASSIC)
    val mapStyle: StateFlow<MapColorStyle> = _mapStyle.asStateFlow()

    private val _routeColor = MutableStateFlow(RouteTrackColor.OCEAN_BLUE)
    val routeColor: StateFlow<RouteTrackColor> = _routeColor.asStateFlow()

    private val _wifiOnlyDownloads = MutableStateFlow(true)
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        _themeMode.value = runCatching {
            AppThemeMode.valueOf(sp.getString(KEY_THEME, AppThemeMode.AUTO.name)!!)
        }.getOrDefault(AppThemeMode.AUTO)

        _mapStyle.value = runCatching {
            MapColorStyle.valueOf(sp.getString(KEY_MAP_STYLE, MapColorStyle.OSM_CLASSIC.name)!!)
        }.getOrDefault(MapColorStyle.OSM_CLASSIC)

        _routeColor.value = runCatching {
            RouteTrackColor.valueOf(sp.getString(KEY_ROUTE_COLOR, RouteTrackColor.OCEAN_BLUE.name)!!)
        }.getOrDefault(RouteTrackColor.OCEAN_BLUE)

        _wifiOnlyDownloads.value = sp.getBoolean(KEY_WIFI_ONLY, true)
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
}

/**
 * Paleta oficial siguiendo guías tonales de Material Design 3 (m3.material.io):
 * tonos azul océano/cielo suaves, superficies tonales confortables y cero marrón dominante.
 */
@Composable
fun rememberNavigatorPalette(): NavigatorPalette {
    val mode by AppPreferencesManager.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.AUTO -> systemDark
    }

    return if (isDark) {
        NavigatorPalette(
            isDark = true,
            bg = Color(0xFF101416),
            surface = Color(0xFF1D2023),
            surfaceAlt = Color(0xFF272A2D),
            hudOverlay = Color(0xEB191C1F),
            border = Color(0xFF40484E),
            textPrimary = Color(0xFFE0E3E6),
            textSecondary = Color(0xFFBFC8CF),
            primary = Color(0xFF87CFF9),
            onPrimary = Color(0xFF00354A),
            secondary = Color(0xFFB0CADC),
            success = Color(0xFF6FD9AC),
            danger = Color(0xFFFFB4AB)
        )
    } else {
        NavigatorPalette(
            isDark = false,
            bg = Color(0xFFF7F9FD),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFECEEF1),
            hudOverlay = Color(0xF5F7F9FD),
            border = Color(0xFFBFC8CF),
            textPrimary = Color(0xFF191C1F),
            textSecondary = Color(0xFF40484E),
            primary = Color(0xFF00506E),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF23759B),
            success = Color(0xFF1B7F5C),
            danger = Color(0xFFBA1A1A)
        )
    }
}
