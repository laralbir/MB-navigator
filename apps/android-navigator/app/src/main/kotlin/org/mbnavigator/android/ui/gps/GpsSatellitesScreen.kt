package org.mbnavigator.android.ui.gps

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.location.GnssConstellation
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.location.SatelliteInfo
import org.mbnavigator.android.core.settings.NavigatorPalette
import org.mbnavigator.android.core.settings.rememberNavigatorPalette
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GpsSatellitesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember { RealLocationTracker.getInstance(context) }

    val location by tracker.currentLocation.collectAsState()
    val palette = rememberNavigatorPalette(location)
    val hasGpsFix by tracker.hasGpsFix.collectAsState()
    val gnss by tracker.gnssSummary.collectAsState()
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission = granted
        if (granted) tracker.startTracking()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            tracker.startTracking()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Estado de Señal y Satélites GNSS",
            color = palette.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Telemetría en vivo GPS · Galileo · GLONASS · BeiDou",
            color = palette.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.secondary)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⚠️ Permiso de ubicación requerido", color = palette.secondary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Conceder Permiso GPS", color = palette.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return
        }

        val signalStatusText = when {
            !gnss.gpsProviderEnabled -> "⚠️ Sensor GPS desactivado en el móvil"
            hasGpsFix && gnss.usedInFixCount >= 6 -> "✓ Fix 3D de Alta Precisión"
            hasGpsFix -> "✓ Posición Fijada"
            gnss.totalVisible > 0 -> "🛰️ Sincronizando Satélites..."
            else -> "📡 Buscando Señal GNSS..."
        }
        val statusColor = when {
            !gnss.gpsProviderEnabled -> palette.danger
            hasGpsFix -> palette.success
            else -> palette.secondary
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.5.dp, statusColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = signalStatusText,
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${gnss.usedInFixCount}/${gnss.totalVisible} Sats",
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBlock("PRECISIÓN", location?.accuracyMeters?.let { "±${it.roundToInt()} m" } ?: "--", palette)
                    MetricBlock("ALTITUD", location?.elevationMeters?.let { "${it.roundToInt()} m" } ?: "--", palette)
                    MetricBlock(
                        "SEÑAL MEDIA",
                        if (gnss.averageCn0DbHz > 0f) String.format(Locale.US, "%.1f dB", gnss.averageCn0DbHz) else "--",
                        palette
                    )
                    MetricBlock("RUMBO", location?.bearingDegrees?.let { "${it.roundToInt()}º" } ?: "--", palette)
                }

                if (location != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            Locale.US,
                            "LAT %.6f   ·   LON %.6f",
                            location!!.latitude,
                            location!!.longitude
                        ),
                        color = palette.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SatelliteSkyplot(
                satellites = gnss.satellites,
                palette = palette,
                modifier = Modifier
                    .size(164.dp)
                    .background(palette.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(8.dp)
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(164.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONSTELACIONES",
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val byConstellation = gnss.satellites.groupBy { it.constellation }
                    listOf(
                        GnssConstellation.GPS,
                        GnssConstellation.GALILEO,
                        GnssConstellation.GLONASS,
                        GnssConstellation.BEIDOU
                    ).forEach { c ->
                        val items = byConstellation[c].orEmpty()
                        val used = items.count { it.usedInFix }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${c.flag} ${c.name}", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$used / ${items.size}",
                                color = if (used > 0) palette.primary else palette.textSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(palette.primary, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("En uso", color = palette.textSecondary, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.size(8.dp).background(palette.secondary, CircleShape))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Visible", color = palette.textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "SATÉLITES DETECTADOS (${gnss.satellites.size})",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (gnss.satellites.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Esperando tramas GNSS del receptor...",
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Acércate a una ventana o al exterior para recibir señal directa de los satélites GPS y Galileo.",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = gnss.satellites,
                    key = { index, sat ->
                        "${sat.constellation.name}-${sat.svid}-$index"
                    }
                ) { _, sat ->
                    SatelliteRow(sat, palette)
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, palette: NavigatorPalette) {
    Column {
        Text(label, color = palette.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SatelliteSkyplot(
    satellites: List<SatelliteInfo>,
    palette: NavigatorPalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = (minOf(size.width, size.height) / 2f) - 12f

        val ringColor = palette.border
        drawCircle(ringColor, radius = maxR, center = Offset(cx, cy), style = Stroke(2f))
        drawCircle(ringColor.copy(alpha = 0.7f), radius = maxR * 0.66f, center = Offset(cx, cy), style = Stroke(1.5f))
        drawCircle(ringColor.copy(alpha = 0.5f), radius = maxR * 0.33f, center = Offset(cx, cy), style = Stroke(1.5f))

        drawLine(ringColor.copy(alpha = 0.6f), Offset(cx, cy - maxR), Offset(cx, cy + maxR), strokeWidth = 1.5f)
        drawLine(ringColor.copy(alpha = 0.6f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 1.5f)

        val labelPaint = android.graphics.Paint().apply {
            color = palette.textSecondary.toArgb()
            textSize = 22f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.apply {
            drawText("N", cx, cy - maxR + 18f, labelPaint)
            drawText("S", cx, cy + maxR - 4f, labelPaint)
            drawText("E", cx + maxR - 10f, cy + 7f, labelPaint)
            drawText("O", cx - maxR + 10f, cy + 7f, labelPaint)
        }

        val satTextPaint = android.graphics.Paint().apply {
            color = palette.textPrimary.toArgb()
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (sat in satellites) {
            val r = maxR * ((90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f)
            val rad = Math.toRadians((sat.azimuthDegrees - 90f).toDouble())
            val sx = cx + (r * cos(rad)).toFloat()
            val sy = cy + (r * sin(rad)).toFloat()
            val dotColor = if (sat.usedInFix) palette.primary else palette.secondary

            drawCircle(dotColor.copy(alpha = 0.25f), radius = 11f, center = Offset(sx, sy))
            drawCircle(dotColor, radius = 6f, center = Offset(sx, sy))
            drawContext.canvas.nativeCanvas.drawText("${sat.svid}", sx, sy - 9f, satTextPaint)
        }
    }
}

@Composable
private fun SatelliteRow(sat: SatelliteInfo, palette: NavigatorPalette) {
    val snrNorm = (sat.cn0DbHz / 50f).coerceIn(0f, 1f)
    val barColor = when {
        sat.usedInFix && sat.cn0DbHz >= 28f -> palette.primary
        sat.cn0DbHz >= 18f -> palette.secondary
        else -> palette.danger
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, if (sat.usedInFix) palette.primary.copy(alpha = 0.45f) else palette.border),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sat.constellation.flag, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.width(105.dp)) {
                Text(
                    text = "PRN ${sat.svid} (${sat.constellation.name})",
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "El ${sat.elevationDegrees.roundToInt()}º · Az ${sat.azimuthDegrees.roundToInt()}º",
                    color = palette.textSecondary,
                    fontSize = 10.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f dB-Hz", sat.cn0DbHz),
                        color = barColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (sat.usedInFix) "EN FIX" else "VISIBLE",
                        color = if (sat.usedInFix) palette.primary else palette.textSecondary,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { snrNorm },
                    color = barColor,
                    trackColor = palette.surfaceAlt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        }
    }
}
