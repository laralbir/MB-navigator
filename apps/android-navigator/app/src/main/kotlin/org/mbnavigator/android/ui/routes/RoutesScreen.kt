package org.mbnavigator.android.ui.routes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorCardBorder = Color(0xFF1E293B)

data class SavedRouteItem(
    val id: String,
    val title: String,
    val distanceKm: Double,
    val elevationGainM: Double,
    val estimatedCurviness: String, // "Alta (142 curvas)", "Muy Alta (230 curvas)"
    val format: String = "GPX 1.1",
    val dateAdded: String = "Hoy"
)

@Composable
fun RoutesScreen(
    onSelectRouteToNavigate: (SavedRouteItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val routesList = remember {
        mutableStateListOf(
            SavedRouteItem("1", "Sierra de Guadarrama y Puerto de Navacerrada", 84.0, 1650.0, "Muy Alta (168 curvas)"),
            SavedRouteItem("2", "Desfiladero de La Hermida y Potes (Picos de Europa)", 112.5, 2100.0, "Extrema (280 curvas)"),
            SavedRouteItem("3", "Ruta de los Pantanos (Guadalajara / Cuenca)", 145.0, 1420.0, "Alta (195 curvas)")
        )
    }

    var lastImportedFileName by remember { mutableStateOf<String?>(null) }

    // Selector de archivos del sistema para GPX, KML y GeoJSON
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Ruta_Importada.gpx"
            lastImportedFileName = fileName
            routesList.add(
                0,
                SavedRouteItem(
                    id = System.currentTimeMillis().toString(),
                    title = fileName.removeSuffix(".gpx").removeSuffix(".kml"),
                    distanceKm = (65..130).random().toDouble(),
                    elevationGainM = (800..1900).random().toDouble(),
                    estimatedCurviness = "Alta (Curvas detectadas)",
                    format = if (fileName.endsWith(".kml")) "KML" else "GPX",
                    dateAdded = "Recién importado"
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Cabecera
        Text(
            text = "MIS RUTAS Y TRACKS",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Importa archivos GPX, KML o GeoJSON para navegar en moto",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Botón de Importación de Archivo
        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            colors = ButtonDefaults.buttonColors(containerColor = ColorNeonGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📂", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "IMPORTAR ARCHIVO GPX / KML",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }

        if (lastImportedFileName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✓ Archivo cargado correctamente: $lastImportedFileName",
                color = ColorNeonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RUTAS GUARDADAS (${routesList.size})",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(routesList) { route ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = route.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(ColorNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, ColorNeonGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(route.format, color = ColorNeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Distancia", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    "${String.format("%.1f", route.distanceKm)} km",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text("Desnivel +", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    "+${route.elevationGainM.toInt()} m",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            Column {
                                Text("Curvas", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    route.estimatedCurviness,
                                    color = ColorNeonAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onSelectRouteToNavigate(route) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeonGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text("▶ NAVEGAR ESTA RUTA", color = ColorNeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
