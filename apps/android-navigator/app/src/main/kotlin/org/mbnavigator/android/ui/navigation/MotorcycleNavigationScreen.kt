package org.mbnavigator.android.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import kotlin.math.roundToInt

// Paleta de Color Racing / Asfalto para Moto
private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorDangerRed = Color(0xFFFF3D00)
private val ColorCardBorder = Color(0xFF1E293B)

data class RouteCheckpoint(
    val name: String,
    val distanceKm: Double,
    val maneuver: String,
    val speedLimitKmh: Int,
    val tempCelsius: Double,
    val windGustKmh: Double,
    val rainRisk: Boolean,
    val hasDgtAlert: Boolean = false,
    val dgtAlertDesc: String? = null
)

@Composable
fun MotorcycleNavigationScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val realLocationTracker = remember { RealLocationTracker(context) }
    val realLocation by realLocationTracker.currentLocation.collectAsState()
    val isTrackingActive by realLocationTracker.isTrackingActive.collectAsState()
    val hasGpsFix by realLocationTracker.hasGpsFix.collectAsState()

    var hasPermission by remember { mutableStateOf(realLocationTracker.hasPermission()) }
    var isLiveGpsMode by remember { mutableStateOf(true) } // Por defecto: Modo GPS Real
    var isSimulating by remember { mutableStateOf(false) }
    var gloveModeEnabled by remember { mutableStateOf(true) }
    var selectedProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }

    // Gestor de permisos en tiempo de ejecución
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) {
            realLocationTracker.startTracking()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            realLocationTracker.startTracking()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            realLocationTracker.stopTracking()
        }
    }

    // Ruta de simulación alternativa (Madrid -> Navacerrada -> Segovia)
    val checkpoints = remember {
        listOf(
            RouteCheckpoint("Salida: M-607", 0.0, "Inicia ruta hacia M-607", 100, 16.0, 15.0, false),
            RouteCheckpoint("M-607 km 28", 28.5, "En 500 m: Salida hacia Cerceda / Navacerrada", 90, 15.0, 20.0, false),
            RouteCheckpoint("M-601 Inicio Subida al Puerto", 45.0, "Precaución: Comienza tramo de curvas enlazadas", 60, 12.0, 28.0, false),
            RouteCheckpoint("M-601 Tramo Curvas de Montaña", 52.0, "Atención: Incidencia DGT 3.0 detectada delante", 50, 10.0, 35.0, rainRisk = false, hasDgtAlert = true, dgtAlertDesc = "Vehículo detenido con baliza V16 a 250 m tras la curva"),
            RouteCheckpoint("Puerto de Navacerrada (Cima 1.858 m)", 60.0, "Horquilla cerrada a la izquierda (Curva 180º)", 40, 6.0, 48.0, true),
            RouteCheckpoint("CL-601 Bajada a La Granja", 72.0, "Precaución: Asfalto húmedo en zona de sombra", 60, 9.0, 32.0, true),
            RouteCheckpoint("Llegada: Segovia", 84.0, "Destino alcanzado. ¡Ruta finalizada!", 50, 13.0, 18.0, false)
        )
    }

    var simIndex by remember { mutableIntStateOf(0) }
    var simSpeed by remember { mutableIntStateOf(0) }

    LaunchedEffect(isSimulating) {
        while (isSimulating) {
            delay(2500)
            if (simIndex < checkpoints.size - 1) {
                simIndex++
                val cp = checkpoints[simIndex]
                simSpeed = when {
                    cp.speedLimitKmh <= 50 -> (38..48).random()
                    cp.speedLimitKmh <= 70 -> (55..68).random()
                    else -> (88..102).random()
                }
            } else {
                isSimulating = false
                simSpeed = 0
            }
        }
    }

    // Datos de telemetría a mostrar: en Vivo (Hardware GPS) o Simulado
    val activeSpeedKmh = if (isLiveGpsMode) {
        ((realLocation?.speedMps ?: 0f) * 3.6f).roundToInt()
    } else {
        simSpeed
    }

    val activeManeuver = if (isLiveGpsMode) {
        if (realLocation != null) "Navegación GPS Activa • Siguiendo rumbo" else "Esperando señal de satélites GPS..."
    } else {
        checkpoints[simIndex].maneuver
    }

    val activeSpeedLimit = if (isLiveGpsMode) 90 else checkpoints[simIndex].speedLimitKmh

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // -------------------------------------------------------------
            // A. TURN-BY-TURN HUD SUPERIOR (Maniobras para casco/manillar)
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorNeonGreen)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (gloveModeEnabled) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (gloveModeEnabled) 58.dp else 48.dp)
                            .background(ColorNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, ColorNeonGreen, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLiveGpsMode) "▲" else "↺",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorNeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isLiveGpsMode) "GPS REAL EN VIVO" else "MODO SIMULACIÓN",
                                color = if (isLiveGpsMode) ColorNeonGreen else ColorNeonAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            if (isLiveGpsMode && hasGpsFix) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("• 3D FIX", color = ColorNeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = activeManeuver,
                            color = Color.White,
                            fontSize = if (gloveModeEnabled) 15.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // B. ALERTA DGT 3.0 / BALIZA V16
            // -------------------------------------------------------------
            val showDgtAlert = !isLiveGpsMode && checkpoints[simIndex].hasDgtAlert
            AnimatedVisibility(visible = showDgtAlert) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorDangerRed.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorDangerRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "ALERTA DGT 3.0: VEHÍCULO EN CALZADA A 250 m",
                                color = ColorDangerRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                            Text(
                                "Baliza V16 conectada activa tras la curva",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // C. LIENZO DEL MAPA INTERACTIVO Y CURSOR GPS DE LA MOTO
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ColorSlateSurface)
                    .border(1.dp, ColorCardBorder, RoundedCornerShape(16.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Dibujar carretera / trazado
                    val roadPath = Path().apply {
                        moveTo(w * 0.15f, h * 0.85f)
                        cubicTo(w * 0.35f, h * 0.7f, w * 0.2f, h * 0.45f, w * 0.5f, h * 0.45f)
                        cubicTo(w * 0.8f, h * 0.45f, w * 0.7f, h * 0.2f, w * 0.85f, h * 0.15f)
                    }

                    // Asfalto y línea verde
                    drawPath(roadPath, color = Color(0xFF1E293B), style = Stroke(width = 34f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(roadPath, color = ColorNeonGreen.copy(alpha = 0.6f), style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // 2. Coordenadas del cursor de la moto
                    val (cursorX, cursorY, bearing) = if (isLiveGpsMode) {
                        // En modo GPS Real: centro de la pantalla con rumbo del sensor
                        val b = realLocation?.bearingDegrees ?: 0f
                        Triple(w * 0.5f, h * 0.5f, b)
                    } else {
                        // En modo simulación: avanza por el trazado
                        val progress = (simIndex.toFloat() / (checkpoints.size - 1).toFloat()).coerceIn(0f, 1f)
                        Triple(w * (0.15f + progress * 0.7f), h * (0.85f - progress * 0.7f), 45f)
                    }

                    // Círculo de precisión GPS (pulso radar)
                    drawCircle(ColorNeonGreen.copy(alpha = 0.2f), radius = 34f, center = Offset(cursorX, cursorY))
                    drawCircle(ColorNeonGreen.copy(alpha = 0.4f), radius = 22f, center = Offset(cursorX, cursorY))

                    // Cursor de flecha / moto rotando según rumbo real
                    rotate(degrees = bearing, pivot = Offset(cursorX, cursorY)) {
                        val arrowPath = Path().apply {
                            moveTo(cursorX, cursorY - 20f)
                            lineTo(cursorX + 14f, cursorY + 16f)
                            lineTo(cursorX, cursorY + 10f)
                            lineTo(cursorX - 14f, cursorY + 16f)
                            close()
                        }
                        drawPath(arrowPath, color = ColorNeonGreen)
                        drawCircle(Color.White, radius = 4f, center = Offset(cursorX, cursorY))
                    }
                }

                // Overlay de información del GPS en tiempo real
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    if (isLiveGpsMode) {
                        if (realLocation != null) {
                            Text(
                                text = "LAT: ${String.format("%.5f", realLocation!!.latitude)}  LON: ${String.format("%.5f", realLocation!!.longitude)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "ALT: ${realLocation!!.elevationMeters?.roundToInt() ?: 0} m  •  PRECISIÓN: ±${realLocation!!.accuracyMeters?.roundToInt() ?: 0} m",
                                color = ColorNeonGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        } else {
                            Text(
                                text = if (!hasPermission) "⚠️ Permiso de ubicación no concedido" else "🛰️ Adquiriendo satélites GPS...",
                                color = ColorNeonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Ruta: ${checkpoints[simIndex].name}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Perfil: ${selectedProfile.displayName}",
                            color = ColorNeonAmber,
                            fontSize = 11.sp
                        )
                    }
                }

                // Selector rápido: GPS Real vs Simulación
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Button(
                        onClick = {
                            isLiveGpsMode = !isLiveGpsMode
                            if (isLiveGpsMode) {
                                isSimulating = false
                                realLocationTracker.startTracking()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiveGpsMode) ColorNeonGreen else Color(0xFF1E293B),
                            contentColor = if (isLiveGpsMode) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isLiveGpsMode) "🛰️ GPS REAL" else "🎮 MODO SIMULACIÓN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // D. TIRA METEOROLÓGICA PREDICTIVA (Open-Meteo & AEMET)
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⛅", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLiveGpsMode) "17ºC" else "${checkpoints[simIndex].tempCelsius.roundToInt()}ºC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💨", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLiveGpsMode) "14 km/h" else "${checkpoints[simIndex].windGustKmh.roundToInt()} km/h",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Text("Asfalto seco", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // E. PANEL INFERIOR: VELOCÍMETRO DIGITAL Y CONTROLES MOTO
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tarjeta Velocímetro Gigante (High Contrast HUD)
                Card(
                    modifier = Modifier.weight(1.2f),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isOverSpeed = activeSpeedKmh > activeSpeedLimit
                        Text(
                            text = "$activeSpeedKmh",
                            fontSize = if (gloveModeEnabled) 44.sp else 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOverSpeed) ColorDangerRed else ColorNeonGreen
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("km/h", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$activeSpeedLimit",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Tarjeta de Controles
                Card(
                    modifier = Modifier.weight(1.8f),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (isLiveGpsMode) {
                                    if (isTrackingActive) realLocationTracker.stopTracking()
                                    else realLocationTracker.startTracking()
                                } else {
                                    if (simIndex >= checkpoints.size - 1) simIndex = 0
                                    isSimulating = !isSimulating
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrackingActive || isSimulating) ColorDangerRed else ColorNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (gloveModeEnabled) 46.dp else 38.dp)
                        ) {
                            Text(
                                text = if (isLiveGpsMode) {
                                    if (isTrackingActive) "⏹ DETENER GPS" else "▶ ACTIVAR GPS REAL"
                                } else {
                                    if (isSimulating) "⏹ DETENER SIM" else "▶ INICIAR SIM"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Modo Guantes", color = Color.White, fontSize = 11.sp)
                            Switch(
                                checked = gloveModeEnabled,
                                onCheckedChange = { gloveModeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ColorNeonGreen,
                                    checkedTrackColor = ColorSlateSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
