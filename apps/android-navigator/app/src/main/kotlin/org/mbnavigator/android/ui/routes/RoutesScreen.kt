package org.mbnavigator.android.ui.routes

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.mbnavigator.android.core.location.RealLocationTracker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.routing.SavedRouteEntry
import org.mbnavigator.android.core.settings.rememberNavigatorPalette
import java.util.Locale

@Composable
fun RoutesScreen(
    onSelectRouteToNavigate: (SavedRouteEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember { RealLocationTracker.getInstance(context) }
    val realLocation by tracker.currentLocation.collectAsState()
    val palette = rememberNavigatorPalette(realLocation)

    LaunchedEffect(Unit) {
        NavigationSessionRepository.ensureInitialized(context)
    }

    val routesList by NavigationSessionRepository.savedRoutes.collectAsState()
    var importFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val res = NavigationSessionRepository.importRouteFromUri(context, uri)
            importFeedback = res.fold(
                onSuccess = { entry ->
                    true to "✓ Importada: ${entry.title} (${entry.points.size} puntos GPS · ${String.format(Locale.US, "%.1f", entry.distanceKm)} km)"
                },
                onFailure = { err ->
                    false to "⚠️ Error al leer track: ${err.message ?: "Formato no válido"}"
                }
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
            text = "Mis Rutas y Tracks",
            color = palette.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "Compatible con GPX 1.1 / 1.2 (MyRoute-App), KML y GeoJSON",
            color = palette.textSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📂", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Importar GPX / KML / GeoJSON",
                    color = palette.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }

        if (importFeedback != null) {
            val (ok, msg) = importFeedback!!
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = if (ok) palette.success else palette.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "RUTAS DISPONIBLES (${routesList.size})",
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(routesList, key = { it.id }) { route ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.border),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = route.title,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(palette.primary.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                    .border(1.dp, palette.primary, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(route.format, color = palette.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Distancia", color = palette.textSecondary, fontSize = 11.sp)
                                Text(
                                    "${String.format(Locale.US, "%.1f", route.distanceKm)} km",
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text("Desnivel +", color = palette.textSecondary, fontSize = 11.sp)
                                Text(
                                    "+${route.elevationGainM.toInt()} m",
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text("Trazado", color = palette.textSecondary, fontSize = 11.sp)
                                Text(
                                    "${route.points.size} pts · ${route.curveCount} curvas",
                                    color = palette.secondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    NavigationSessionRepository.startNavigatingRoute(route, previewSimulation = false)
                                    onSelectRouteToNavigate(route)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "▶ Navegar",
                                    color = palette.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    NavigationSessionRepository.startNavigatingRoute(route, previewSimulation = true)
                                    onSelectRouteToNavigate(route)
                                },
                                border = BorderStroke(1.dp, palette.secondary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("👁️ Previo", color = palette.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val gpxFile = NavigationSessionRepository.exportRouteToShareableGpxFile(context, route)
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            gpxFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/gpx+xml"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "${route.title}.gpx")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Compartir ruta GPX: ${route.title}")
                                        )
                                    }.onFailure { err ->
                                        importFeedback = false to "⚠️ No se pudo compartir GPX: ${err.message}"
                                    }
                                },
                                border = BorderStroke(1.dp, palette.border),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                            ) {
                                Text("📤", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
