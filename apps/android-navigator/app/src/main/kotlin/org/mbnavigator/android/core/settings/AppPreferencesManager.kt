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
    CARTO_VOYAGER("Voyager Color", "🎨 Voyager", "Mapa moderno claro con carreteras y relieve suave"),
    OSM_CLASSIC("OSM Clásico", "🗺️ OSM", "OpenStreetMap detallado estándar a todo color"),
    OPEN_TOPO("Topográfico", "⛰️ Topo", "Curvas de nivel, puertos de montaña y valles"),
    CARTO_POSITRON("Plata Minimal", "🏔️ Minimal", "Fondo gris claro de alto contraste para destacar la ruta"),
    CARTO_DARK("Nocturno Zafiro", "🌌 Nocturno", "Mapa oscuro diseñado nativamente para conducción nocturna")
}

enum class RouteTrackColor(
    val label: String,
    val mainHex: String,
    val borderHex: String,
    val composeColor: Color
) {
    COBALT_BLUE("Azul Cobalto", "#2563EB", "#1E3A8A", Color(0xFF2563EB)),
    TOURING_ORANGE("Naranja Touring", "#F97316", "#9A3412", Color(0xFFF97316)),
    ROYAL_PURPLE("Violeta Ruta", "#7C3AED", "#4C1D95", Color(0xFF7C3AED)),
    CRIMSON_CORAL("Rojo Rally", "#E11D48", "#881337", Color(0xFFE11D48))
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

    private val _mapStyle = MutableStateFlow(MapColorStyle.CARTO_VOYAGER)
    val mapStyle: StateFlow<MapColorStyle> = _mapStyle.asStateFlow()

    private val _routeColor = MutableStateFlow(RouteTrackColor.COBALT_BLUE)
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
            MapColorStyle.valueOf(sp.getString(KEY_MAP_STYLE, MapColorStyle.CARTO_VOYAGER.name)!!)
        }.getOrDefault(MapColorStyle.CARTO_VOYAGER)

        _routeColor.value = runCatching {
            RouteTrackColor.valueOf(sp.getString(KEY_ROUTE_COLOR, RouteTrackColor.COBALT_BLUE.name)!!)
        }.getOrDefault(RouteTrackColor.COBALT_BLUE)

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
            bg = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            surfaceAlt = Color(0xFF334155),
            hudOverlay = Color(0xEE1E293B),
            border = Color(0xFF334155),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF0F172A),
            secondary = Color(0xFFFB923C),
            success = Color(0xFF10B981),
            danger = Color(0xFFF87171)
        )
    } else {
        NavigatorPalette(
            isDark = false,
            bg = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFE2E8F0),
            hudOverlay = Color(0xF5FFFFFF),
            border = Color(0xFFCBD5E1),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            primary = Color(0xFF2563EB),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFFF97316),
            success = Color(0xFF059669),
            danger = Color(0xFFDC2626)
        )
    }
}
