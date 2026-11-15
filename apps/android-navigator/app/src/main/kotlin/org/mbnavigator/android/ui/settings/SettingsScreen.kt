package org.mbnavigator.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.routing.OpenRoutingClient
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.AppThemeMode
import org.mbnavigator.android.core.settings.FavoriteDestination
import org.mbnavigator.android.core.settings.FavoritePlaceCategory
import org.mbnavigator.android.core.settings.MapColorStyle
import org.mbnavigator.android.core.settings.MapOrientationMode
import org.mbnavigator.android.core.settings.NavigatorPalette
import org.mbnavigator.android.core.settings.RouteTrackColor
import org.mbnavigator.android.core.settings.SystemNavBarMode
import org.mbnavigator.android.core.settings.rememberNavigatorPalette
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.voice.AndroidTextToSpeechService
import org.mbnavigator.android.core.voice.VoiceVerbosityLevel
import org.mbnavigator.android.ui.maps.OfflineMapsScreen
import java.util.Locale

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    var showOfflineMapsManager by remember { mutableStateOf(false) }

    if (showOfflineMapsManager) {
        OfflineMapsScreen(
            onBackToSettings = { showOfflineMapsManager = false },
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracker = remember { RealLocationTracker.getInstance(context) }
    val currentLocation by tracker.currentLocation.collectAsState()
    val palette = rememberNavigatorPalette(currentLocation)
    val ttsService = remember { AndroidTextToSpeechService(context) }
    val favorites by AppPreferencesManager.favoriteDestinations.collectAsState()

    val themeMode by AppPreferencesManager.themeMode.collectAsState()
    val mapStyle by AppPreferencesManager.mapStyle.collectAsState()
    val routeColor by AppPreferencesManager.routeColor.collectAsState()
    val wifiOnly by AppPreferencesManager.wifiOnlyDownloads.collectAsState()
    val systemNavBarMode by AppPreferencesManager.systemNavBarMode.collectAsState()
    val autoNightMap by AppPreferencesManager.autoNightMapEnabled.collectAsState()
    val mapOrientationMode by AppPreferencesManager.mapOrientationMode.collectAsState()
    val speedAutoZoom by AppPreferencesManager.speedAutoZoomEnabled.collectAsState()
    val trafficLayerEnabled by AppPreferencesManager.trafficLayerEnabled.collectAsState()
    val autoRecalcEnabled by AppPreferencesManager.autoRecalculateEnabled.collectAsState()
    val voiceGuidanceEnabled by AppPreferencesManager.voiceGuidanceEnabled.collectAsState()
    val voiceVerbosity by AppPreferencesManager.voiceVerbosity.collectAsState()
    val speechSpeedRate by AppPreferencesManager.voiceSpeechRate.collectAsState()
    val voiceLanguageTag by AppPreferencesManager.voiceLanguageTag.collectAsState()
    val dgtAlertsEnabled by AppPreferencesManager.dgtAlertsEnabled.collectAsState()
    val speedCameraAlertsEnabled by AppPreferencesManager.speedCameraAlertsEnabled.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            ttsService.shutdown()
        }
    }

    var geminiAiEnabled by remember { mutableStateOf(false) }
    var geminiApiKey by remember { mutableStateOf("") }
    var favSearchQuery by remember { mutableStateOf("") }
    var favFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Ajustes y Preferencias",
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Apariencia, estilos de mapa, mapas sin conexión, audio y seguridad",
            color = palette.textSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 1. MAPAS OFFLINE, TEMA CLARO/OSCURO Y COLORES DE MAPA
        // -----------------------------------------------------------------
        SectionTitle("MAPAS Y APARIENCIA VISUAL", palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Acceso exclusivo al gestor jerárquico de mapas offline
                Button(
                    onClick = { showOfflineMapsManager = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🗺️ Mapas Offline (Regiones y Versiones)",
                            color = palette.onPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Abrir ›",
                            color = palette.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                SettingsToggleRow(
                    title = "Descargar y actualizar solo por Wi-Fi",
                    subtitle = if (wifiOnly) "Protegido contra gasto de datos móviles" else "Permitidas descargas usando datos 4G/5G",
                    checked = wifiOnly,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setWifiOnlyDownloads(it) }
                )

                // Selector Tono Claro / Oscuro / Sensor / Crepúsculo
                Text(
                    text = "Modo Día / Noche Automático",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.chunked(2).forEach { rowModes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowModes.forEach { mode ->
                                SelectableOptionPill(
                                    title = "${mode.icon} ${mode.label}",
                                    isSelected = themeMode == mode,
                                    palette = palette,
                                    modifier = Modifier.weight(1f),
                                    onClick = { AppPreferencesManager.setThemeMode(mode) }
                                )
                            }
                        }
                    }
                }
                Text(
                    text = when (themeMode) {
                        AppThemeMode.SYSTEM_SENSOR -> "Sigue el sensor del dispositivo y Android Auto."
                        AppThemeMode.TWILIGHT_HOURS -> "Cambia automáticamente según amanecer y ocaso solar de tu posición GPS."
                        AppThemeMode.LIGHT -> "Interfaz siempre en modo día luminoso."
                        AppThemeMode.DARK -> "Interfaz siempre en modo nocturno suave."
                    },
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )

                SettingsToggleRow(
                    title = "Mapa Nocturno automático al anochecer",
                    subtitle = if (autoNightMap) "Pasa el mapa a tono pizarra azulada en modo noche" else "Mantiene siempre el estilo de mapa elegido",
                    checked = autoNightMap,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setAutoNightMapEnabled(it) }
                )

                Text(
                    text = "Barra Inferior de Android",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SystemNavBarMode.entries.forEach { mode ->
                        SelectableOptionPill(
                            title = mode.label,
                            isSelected = systemNavBarMode == mode,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = { AppPreferencesManager.setSystemNavBarMode(mode) }
                        )
                    }
                }
                Text(
                    text = systemNavBarMode.description,
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )

                Text(
                    text = "Orientación del Mapa en Navegación",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapOrientationMode.entries.forEach { mode ->
                        SelectableOptionPill(
                            title = mode.shortLabel,
                            isSelected = mapOrientationMode == mode,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = { AppPreferencesManager.setMapOrientationMode(mode) }
                        )
                    }
                }
                Text(
                    text = mapOrientationMode.description,
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )

                SettingsToggleRow(
                    title = "Zoom automático según velocidad",
                    subtitle = if (speedAutoZoom) "Acerca el mapa en ciudad (<25 km/h) y amplía vista en carretera (>90 km/h)" else "Zoom manual fijo sin escalado por velocidad",
                    checked = speedAutoZoom,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setSpeedAutoZoomEnabled(it) }
                )

                SettingsToggleRow(
                    title = "Capa de densidad de tráfico por colores",
                    subtitle = if (trafficLayerEnabled) "Muestra retenciones e incidencias reales DGT en verde, ámbar, naranja y rojo" else "Ocultar capa visual de tráfico",
                    checked = trafficLayerEnabled,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setTrafficLayerEnabled(it) }
                )

                SettingsToggleRow(
                    title = "Recálculo automático fuera de ruta",
                    subtitle = if (autoRecalcEnabled) "Recalcula en marcha si te desvías del recorrido activo" else "Solo muestra aviso sin recalcular automáticamente",
                    checked = autoRecalcEnabled,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setAutoRecalculateEnabled(it) }
                )

                // Selector Estilo / Color de Mapa
                Text(
                    text = "Estilo y Color del Mapa",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MapColorStyle.entries) { style ->
                        val selected = mapStyle == style
                        Surface(
                            modifier = Modifier.clickable { AppPreferencesManager.setMapStyle(style) },
                            color = if (selected) palette.primary.copy(alpha = 0.14f) else palette.surfaceAlt,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) palette.primary else palette.border
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = style.shortLabel,
                                    color = if (selected) palette.primary else palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = style.label,
                                    color = palette.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                Text(
                    text = mapStyle.description,
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )

                // Selector Color de Trazado GPX
                Text(
                    text = "Color del Trazado de Ruta",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RouteTrackColor.entries.forEach { colorOpt ->
                        val selected = routeColor == colorOpt
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable { AppPreferencesManager.setRouteColor(colorOpt) },
                            color = if (selected) colorOpt.composeColor.copy(alpha = 0.18f) else palette.surfaceAlt,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colorOpt.composeColor else palette.border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(colorOpt.composeColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = colorOpt.label.substringBefore(" "),
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 2. SÍNTESIS DE VOZ (TTS) Y VOCES PARA CASCO
        // -----------------------------------------------------------------
        SectionTitle("GUÍA POR VOZ E INTERCOMUNICADOR (TTS)", palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingsToggleRow(
                    title = "Indicaciones de giro por voz",
                    subtitle = if (voiceGuidanceEnabled) {
                        "Activas en cada maniobra de ruta"
                    } else {
                        "Silenciadas; radares, accidentes y vehículos averiados siguen avisando"
                    },
                    checked = voiceGuidanceEnabled,
                    palette = palette,
                    onCheckedChange = { enabled ->
                        AppPreferencesManager.setVoiceGuidanceEnabled(enabled)
                        if (!enabled) ttsService.stopSpeaking()
                    }
                )
                HorizontalDivider(color = palette.border, modifier = Modifier.padding(vertical = 10.dp))
                Text("Nivel de Verbosidad de Indicaciones", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableOptionPill("Mínima", voiceVerbosity == VoiceVerbosityLevel.MINIMAL, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceVerbosity(VoiceVerbosityLevel.MINIMAL)
                    }
                    SelectableOptionPill("Estándar", voiceVerbosity == VoiceVerbosityLevel.STANDARD, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceVerbosity(VoiceVerbosityLevel.STANDARD)
                    }
                    SelectableOptionPill("Detallada", voiceVerbosity == VoiceVerbosityLevel.DETAILED, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceVerbosity(VoiceVerbosityLevel.DETAILED)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when (voiceVerbosity) {
                        VoiceVerbosityLevel.MINIMAL -> "• Solo giros a menos de 100 m y alertas de peligro crítico."
                        VoiceVerbosityLevel.STANDARD -> "• Aviso previo a 500 m y confirmación de giro."
                        VoiceVerbosityLevel.DETAILED -> "• Carril recomendado, nombre de vía y advertencias meteorológicas."
                    },
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Idioma y Velocidad de Voz", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableOptionPill("Español (ES)", voiceLanguageTag == "es-ES", palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceLanguageTag("es-ES")
                        ttsService.setLanguage(Locale("es", "ES"))
                    }
                    SelectableOptionPill("LATAM", voiceLanguageTag == "es-US", palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceLanguageTag("es-US")
                        ttsService.setLanguage(Locale("es", "US"))
                    }
                    SelectableOptionPill("English", voiceLanguageTag == "en-US", palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceLanguageTag("en-US")
                        ttsService.setLanguage(Locale.US)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableOptionPill("0.85x", speechSpeedRate == 0.85f, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceSpeechRate(0.85f)
                        ttsService.setSpeechRate(0.85f)
                    }
                    SelectableOptionPill("1.0x Normal", speechSpeedRate == 1.0f, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceSpeechRate(1.0f)
                        ttsService.setSpeechRate(1.0f)
                    }
                    SelectableOptionPill("1.15x Ágil", speechSpeedRate == 1.15f, palette, Modifier.weight(1f)) {
                        AppPreferencesManager.setVoiceSpeechRate(1.15f)
                        ttsService.setSpeechRate(1.15f)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            ttsService.speak("En trescientos cincuenta metros, curva cerrada a la derecha hacia Morella")
                        },
                        border = BorderStroke(1.dp, palette.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("🔊 Probar Voz", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val fallbackIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallbackIntent)
                            }
                        },
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("⬇️ Voces Sistema", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 3. COPILOTO GEMINI Y OBTENCIÓN DE API KEY
        // -----------------------------------------------------------------
        SectionTitle("COPILOTO CON INTELIGENCIA ARTIFICIAL", palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                SettingsToggleRow(
                    title = "Habilitar Asistente Google Gemini",
                    subtitle = if (geminiAiEnabled) "Modo Nube activo con clave personal" else "Modo 100% Offline Determinista",
                    checked = geminiAiEnabled,
                    palette = palette,
                    onCheckedChange = { geminiAiEnabled = it }
                )

                if (geminiAiEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("🔑 Obtener Clave API en Google AI Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Abre Google AI Studio con el botón superior.\n" +
                            "2. Pulsa 'Create API Key' y copia tu clave gratuita.\n" +
                            "3. Pégala a continuación:",
                        color = palette.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Clave de API de Gemini") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.border
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 4. DESTINOS FAVORITOS (CASA, TRABAJO, PERSONALIZADOS)
        // -----------------------------------------------------------------
        SectionTitle("DESTINOS FAVORITOS", palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configura Casa, Trabajo o puntos frecuentes para navegar en un toque",
                    color = palette.textSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = favSearchQuery,
                    onValueChange = { favSearchQuery = it },
                    label = { Text("Nombre o dirección del destino") },
                    placeholder = { Text("Ej: Casa, Taller Moto, Morella...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FavoritePlaceCategory.entries.forEach { cat ->
                        OutlinedButton(
                            onClick = {
                                val loc = currentLocation
                                val query = favSearchQuery.trim()
                                scope.launch {
                                    if (query.isNotBlank()) {
                                        val res = OpenRoutingClient.searchDestination(query)
                                        val first = res.getOrNull()?.firstOrNull()
                                        if (first != null) {
                                            val favId = when (cat) {
                                                FavoritePlaceCategory.HOME -> "fav_home"
                                                FavoritePlaceCategory.WORK -> "fav_work"
                                                FavoritePlaceCategory.CUSTOM -> "fav_${System.currentTimeMillis()}"
                                            }
                                            AppPreferencesManager.saveFavoriteDestination(
                                                FavoriteDestination(
                                                    id = favId,
                                                    name = if (cat == FavoritePlaceCategory.CUSTOM) first.shortName else cat.label,
                                                    category = cat,
                                                    address = first.displayName,
                                                    latitude = first.location.latitude,
                                                    longitude = first.location.longitude
                                                )
                                            )
                                            favFeedback = "✓ Guardado ${cat.icon} ${first.shortName}"
                                            favSearchQuery = ""
                                            return@launch
                                        }
                                    }
                                    if (loc != null) {
                                        val favId = when (cat) {
                                            FavoritePlaceCategory.HOME -> "fav_home"
                                            FavoritePlaceCategory.WORK -> "fav_work"
                                            FavoritePlaceCategory.CUSTOM -> "fav_${System.currentTimeMillis()}"
                                        }
                                        val labelName = if (cat == FavoritePlaceCategory.CUSTOM && query.isNotBlank()) query else cat.label
                                        AppPreferencesManager.saveFavoriteDestination(
                                            FavoriteDestination(
                                                id = favId,
                                                name = labelName,
                                                category = cat,
                                                address = "Posición GPS actual",
                                                latitude = loc.latitude,
                                                longitude = loc.longitude
                                            )
                                        )
                                        favFeedback = "✓ Guardado en GPS actual: ${cat.icon} $labelName"
                                        favSearchQuery = ""
                                    } else {
                                        favFeedback = "⚠️ Escribe una dirección o activa GPS para guardar."
                                    }
                                }
                            },
                            border = BorderStroke(1.dp, palette.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+ ${cat.icon} ${cat.label}",
                                color = palette.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                favFeedback?.let { msg ->
                    Text(msg, color = palette.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                favorites.forEach { fav ->
                    Surface(
                        color = palette.surfaceAlt,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${fav.category.icon} ${fav.name}",
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = fav.address.ifBlank { "${fav.latitude}, ${fav.longitude}" },
                                    color = palette.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "✕",
                                color = palette.danger,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { AppPreferencesManager.removeFavoriteDestination(fav.id) }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -----------------------------------------------------------------
        // 5. SEGURIDAD VIAL, TRÁFICO Y DGT 3.0
        // -----------------------------------------------------------------
        SectionTitle("SEGURIDAD VIAL Y TRÁFICO", palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsToggleRow(
                    title = "Alertas DGT 3.0 en Tiempo Real",
                    subtitle = "Balizas V16, vehículos averiados, accidentes y cortes (activos aunque desactives la voz)",
                    checked = dgtAlertsEnabled,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setDgtAlertsEnabled(it) }
                )
                SettingsToggleRow(
                    title = "Aviso de Radares y Límites",
                    subtitle = "Radares fijos y alertas de peligro sonoras/visuales incluso con la voz desactivada",
                    checked = speedCameraAlertsEnabled,
                    palette = palette,
                    onCheckedChange = { AppPreferencesManager.setSpeedCameraAlertsEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(title: String, palette: NavigatorPalette) {
    Text(
        text = title,
        color = palette.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SelectableOptionPill(
    title: String,
    isSelected: Boolean,
    palette: NavigatorPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(if (isSelected) palette.primary else palette.surfaceAlt, RoundedCornerShape(10.dp))
            .border(1.dp, if (isSelected) palette.primary else palette.border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) palette.onPrimary else palette.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    palette: NavigatorPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = palette.textSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = palette.onPrimary,
                checkedTrackColor = palette.primary
            )
        )
    }
}
