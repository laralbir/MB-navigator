package org.mbnavigator.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
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
    // 1. Ruta de prueba: Madrid -> Puerto de Navacerrada -> Segovia
    val checkpoints = remember {
        listOf(
            RouteCheckpoint("Salida: Madrid (M-607)", 0.0, "Sigue recto por M-607", 100, 16.0, 15.0, false),
            RouteCheckpoint("Colmenar Viejo", 28.5, "En 500 m: Mantente a la derecha hacia Cerceda", 90, 15.0, 20.0, false),
            RouteCheckpoint("Cercedilla (Inicio Puerto)", 54.0, "¡Atención! Comienza tramo de curvas reviradas", 60, 12.0, 28.0, false),
            RouteCheckpoint("⚠️ Tramo V16 Activo", 61.2, "Precaución: Baliza V16 DGT 3.0 en arcén derecho", 50, 10.0, 35.0, false, true, "Vehículo detenido en curva ciega km 61"),
            RouteCheckpoint("Puerto de Navacerrada (1.858 m)", 68.0, "Horquilla cerrada a la izquierda (Curva 180º)", 40, 6.0, 48.0, true), // Viento lateral + lluvia
            RouteCheckpoint("Bajada hacia La Granja", 79.5, "Curvas enlazadas en bajada - Asfalto húmedo", 60, 9.0, 32.0, true),
            RouteCheckpoint("Llegada: Segovia (Acueducto)", 92.0, "Has llegado a tu destino. ¡Ruta completada!", 50, 13.0, 18.0, false)
        )
    }

    var isSimulating by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var gloveModeEnabled by remember { mutableStateOf(true) }
    var selectedProfile by remember { mutableStateOf(MotorcycleRoutingProfile.CURVY) }
    var currentSpeed by remember { mutableIntStateOf(0) }
    var totalDistanceDoneKm by remember { mutableFloatStateOf(0f) }

    val activeCheckpoint = checkpoints[currentIndex]

    // Simulación en bucle del GPS mientras está activo
    LaunchedEffect(isSimulating) {
        while (isSimulating) {
            delay(2500)
            if (currentIndex < checkpoints.size - 1) {
                currentIndex++
                totalDistanceDoneKm = activeCheckpoint.distanceKm.toFloat()
                // Variar velocidad según tipo de tramo
                currentSpeed = when {
                    activeCheckpoint.speedLimitKmh <= 50 -> (38..48).random()
                    activeCheckpoint.speedLimitKmh <= 70 -> (55..68).random()
                    else -> (88..102).random()
                }
            } else {
                isSimulating = false
                currentSpeed = 0
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
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
                        .padding(if (gloveModeEnabled) 18.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono de maniobra
                    Box(
                        modifier = Modifier
                            .size(if (gloveModeEnabled) 64.dp else 52.dp)
                            .background(ColorNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, ColorNeonGreen, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (activeCheckpoint.maneuver.contains("izquierda", true)) "↰"
                            else if (activeCheckpoint.maneuver.contains("derecha", true)) "↱"
                            else if (activeCheckpoint.maneuver.contains("Horquilla", true)) "↺"
                            else "↑",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = ColorNeonGreen
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentIndex < checkpoints.size - 1) "En ${(checkpoints[currentIndex + 1].distanceKm - activeCheckpoint.distanceKm).coerceAtLeast(0.5).let { String.format("%.1f km", it) }}" else "Destino alcanzado",
                            color = ColorNeonGreen,
                            fontSize = if (gloveModeEnabled) 20.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = activeCheckpoint.maneuver,
                            color = Color.White,
                            fontSize = if (gloveModeEnabled) 16.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // -------------------------------------------------------------
            // B. ALERTA DGT 3.0 / BALIZA V16 (Si está en el tramo)
            // -------------------------------------------------------------
            AnimatedVisibility(visible = activeCheckpoint.hasDgtAlert) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorDangerRed.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, ColorDangerRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚨", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "ALERTA DGT 3.0: BALIZA V16 CONECTADA",
                                color = ColorDangerRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                activeCheckpoint.dgtAlertDesc ?: "Vehículo averiado en calzada",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // C. LIENZO DEL MAPA INTERACTIVO (Curvas y Telemetría en Moto)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ColorSlateSurface)
                    .border(1.dp, ColorCardBorder, RoundedCornerShape(16.dp))
            ) {
                // Dibujo de la carretera con curvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Dibujar asfalto de la ruta
                    val path = Path().apply {
                        moveTo(w * 0.15f, h * 0.85f)
                        cubicTo(w * 0.3f, h * 0.7f, w * 0.2f, h * 0.45f, w * 0.5f, h * 0.45f)
                        cubicTo(w * 0.8f, h * 0.45f, w * 0.7f, h * 0.2f, w * 0.85f, h * 0.12f)
                    }

                    // Asfalto base oscuro
                    drawPath(path, color = Color(0xFF1E293B), style = Stroke(width = 36f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Trazada Verde Neón (Racing Line)
                    drawPath(path, color = ColorNeonGreen, style = Stroke(width = 10f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // Dibujar posición actual de la moto en la curva
                    val progressRatio = (currentIndex.toFloat() / (checkpoints.size - 1).toFloat()).coerceIn(0f, 1f)
                    val motoX = w * (0.15f + progressRatio * 0.7f)
                    val motoY = h * (0.85f - progressRatio * 0.73f)

                    // Halo de radar GPS
                    drawCircle(ColorNeonGreen.copy(alpha = 0.25f), radius = 28f, center = Offset(motoX, motoY))
                    // Punto central de la moto
                    drawCircle(Color.White, radius = 12f, center = Offset(motoX, motoY))
                    drawCircle(ColorNeonGreen, radius = 7f, center = Offset(motoX, motoY))
                }

                // Overlay de información del mapa
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Ruta: ${activeCheckpoint.name}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Perfil: ${selectedProfile.displayName}",
                        color = ColorNeonAmber,
                        fontSize = 12.sp
                    )
                }

                // Botón de recentrado flotante
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(if (gloveModeEnabled) 56.dp else 44.dp)
                        .background(ColorNeonGreen, CircleShape)
                        .clickable { /* Recentrar */ },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = 22.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (activeCheckpoint.rainRisk) "🌧️" else "⛅", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${activeCheckpoint.tempCelsius.roundToInt()}ºC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Viento lateral (crítico para moto)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💨", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        val isWindHazard = activeCheckpoint.windGustKmh > 40
                        Text(
                            text = "${activeCheckpoint.windGustKmh.roundToInt()} km/h racha",
                            color = if (isWindHazard) ColorNeonAmber else Color.White,
                            fontWeight = if (isWindHazard) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }

                    // Aviso si hay asfalto resbaladizo
                    if (activeCheckpoint.rainRisk) {
                        Text("⚠️ Asfalto mojado", color = ColorNeonAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Asfalto seco", color = ColorNeonGreen, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isOverSpeed = currentSpeed > activeCheckpoint.speedLimitKmh
                        Text(
                            text = "$currentSpeed",
                            fontSize = if (gloveModeEnabled) 48.sp else 40.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isOverSpeed) ColorDangerRed else ColorNeonGreen
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("km/h", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            // Señal de límite de velocidad
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color.White, CircleShape)
                                    .border(2.dp, Color.Red, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${activeCheckpoint.speedLimitKmh}",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Tarjeta de Controles de Simulación y Modo Guantes
                Card(
                    modifier = Modifier.weight(1.8f),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (currentIndex >= checkpoints.size - 1) currentIndex = 0
                                isSimulating = !isSimulating
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSimulating) ColorDangerRed else ColorNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (gloveModeEnabled) 50.dp else 40.dp)
                        ) {
                            Text(
                                text = if (isSimulating) "⏹ DETENER RUTA" else "▶ INICIAR RUTA GPS",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Modo Guantes", color = Color.White, fontSize = 12.sp)
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
