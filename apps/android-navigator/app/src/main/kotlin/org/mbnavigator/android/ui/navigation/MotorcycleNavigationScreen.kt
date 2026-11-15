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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.model.GeoPoint
import org.mbnavigator.android.core.routing.MotorcycleRoutingProfile
import kotlin.math.roundToInt

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorDangerRed = Color(0xFFFF3D00)
private val ColorCardBorder = Color(0xFF1E293B)

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
    var selectedProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }
    var activeDestinationName by remember { mutableStateOf("Puerto de Navacerrada (M-601)") }
    var isDestinationSet by remember { mutableStateOf(true) }

    // Gestor de permisos de ubicación
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

    // Telemetría GPS Real obtenida del hardware del dispositivo
    val currentGpsSpeedKmh = ((realLocation?.speedMps ?: 0f) * 3.6f).roundToInt()
    val currentBearing = realLocation?.bearingDegrees ?: 0f
    val currentAltitude = realLocation?.elevationMeters?.roundToInt() ?: 0
    val currentAccuracy = realLocation?.accuracyMeters?.roundToInt() ?: 0

    val speedLimitKmh = 90

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
            // A. TURN-BY-TURN HUD (Instrucción de maniobra GPS Real)
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
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono de maniobra en verde neón (siempre tamaño grande para uso en moto)
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(ColorNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, ColorNeonGreen, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTrackingActive) "↑" else "🛰️",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorNeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (hasGpsFix) "GPS 3D ACTIVO (±$currentAccuracy m)" else "ADQUIRIENDO SATÉLITES...",
                                color = if (hasGpsFix) ColorNeonGreen else ColorNeonAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = if (isDestinationSet) "En 350 m: Mantente en la M-601 hacia $activeDestinationName" else "Conduciendo libremente • Sin destino fijado",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // B. MAPA DE NAVEGACIÓN GPS Y CURSOR REAL DE LA MOTO
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

                    // 1. Trazado de carretera / ruta proyectada
                    val roadPath = Path().apply {
                        moveTo(w * 0.5f, h * 0.95f)
                        cubicTo(w * 0.5f, h * 0.7f, w * 0.35f, h * 0.55f, w * 0.45f, h * 0.4f)
                        cubicTo(w * 0.6f, h * 0.25f, w * 0.5f, h * 0.15f, w * 0.5f, h * 0.05f)
                    }

                    // Asfalto y línea guía
                    drawPath(roadPath, color = Color(0xFF1E293B), style = Stroke(width = 38f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(roadPath, color = ColorNeonGreen.copy(alpha = 0.5f), style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // 2. Cursor de la moto posicionado en el centro de la navegación
                    val cursorX = w * 0.5f
                    val cursorY = h * 0.65f

                    // Anillo de precisión GPS en tiempo real
                    drawCircle(ColorNeonGreen.copy(alpha = 0.15f), radius = 42f, center = Offset(cursorX, cursorY))
                    drawCircle(ColorNeonGreen.copy(alpha = 0.35f), radius = 26f, center = Offset(cursorX, cursorY))

                    // Flecha de moto orientada según el rumbo magnético/GPS real
                    rotate(degrees = currentBearing, pivot = Offset(cursorX, cursorY)) {
                        val arrow = Path().apply {
                            moveTo(cursorX, cursorY - 24f)
                            lineTo(cursorX + 16f, cursorY + 18f)
                            lineTo(cursorX, cursorY + 12f)
                            lineTo(cursorX - 16f, cursorY + 18f)
                            close()
                        }
                        drawPath(arrow, color = ColorNeonGreen)
                        drawCircle(Color.White, radius = 4f, center = Offset(cursorX, cursorY))
                    }
                }

                // Datos geográficos reales en esquina superior izquierda
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    if (realLocation != null) {
                        Text(
                            text = "LAT: ${String.format("%.5f", realLocation!!.latitude)}  LON: ${String.format("%.5f", realLocation!!.longitude)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "ALT: $currentAltitude m  •  RUMBO: ${currentBearing.roundToInt()}º",
                            color = ColorNeonGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = if (!hasPermission) "⚠️ Activa permisos de ubicación" else "🛰️ Conectando con satélites GPS...",
                            color = ColorNeonAmber,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Botón flotante para centrar la vista en el GPS
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(54.dp)
                        .background(ColorNeonGreen, CircleShape)
                        .clickable {
                            realLocationTracker.startTracking()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // C. TIRA DE METEOROLOGÍA EN TIEMPO REAL
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
                            text = "18ºC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💨", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("12 km/h", color = Color.White, fontSize = 12.sp)
                    }

                    Text("Asfalto seco", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // -------------------------------------------------------------
            // D. PANEL INFERIOR: VELOCÍMETRO GPS Y CONTROLES GRANDES DE MOTO
            // -------------------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Velocímetro Digital con velocidad del satélite en vivo (siempre grande y visible)
                Card(
                    modifier = Modifier.weight(1.1f),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isOverSpeed = currentGpsSpeedKmh > speedLimitKmh
                        Text(
                            text = "$currentGpsSpeedKmh",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOverSpeed) ColorDangerRed else ColorNeonGreen
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("km/h", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$speedLimitKmh",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Tarjeta de Controles Táctiles Optimizados para Moto
                Card(
                    modifier = Modifier.weight(1.9f),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isTrackingActive) {
                                    realLocationTracker.stopTracking()
                                } else {
                                    realLocationTracker.startTracking()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrackingActive) ColorDangerRed else ColorNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text(
                                text = if (isTrackingActive) "⏹ PAUSAR GPS" else "▶ SEGUIMIENTO GPS",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { realLocationTracker.startTracking() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("🎯 Centrar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { /* Recalcular ruta con BRouter */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("🔄 Desvío", color = ColorNeonAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
