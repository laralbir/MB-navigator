package org.mbnavigator.android.ui.maps

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorCardBorder = Color(0xFF1E293B)

data class OfflineMapPackage(
    val id: String,
    val name: String,
    val description: String,
    val sizeMb: Int,
    var isDownloaded: Boolean = false,
    var isDownloading: Boolean = false,
    var downloadProgress: Float = 0f
)

@Composable
fun OfflineMapsScreen(
    modifier: Modifier = Modifier
) {
    val mapPackages = remember {
        mutableStateListOf(
            OfflineMapPackage("1", "España: Zona Centro y Madrid", "Guadarrama, Gredos, Montes de Toledo", 385, isDownloaded = true),
            OfflineMapPackage("2", "España: Pirineos y Cataluña", "Pirineo Aragonés, Valle de Arán, Costa Brava", 540, isDownloaded = false),
            OfflineMapPackage("3", "España: Picos de Europa y Norte", "Asturias, Cantabria, País Vasco", 420, isDownloaded = false),
            OfflineMapPackage("4", "España: Andalucía y Sierra Nevada", "Ronda, Alpujarras, Sierra de Cazorla", 610, isDownloaded = false),
            OfflineMapPackage("5", "Francia: Pirineos Franceses y Alpes", "Col del Tourmalet, Aubisque, Route des Grandes Alpes", 790, isDownloaded = false)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Cabecera
        Text(
            text = "MAPAS SIN CONEXIÓN (OFFLINE)",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Mapas vectoriales libres (OpenStreetMap / PMTiles) sin conexión ni consumo de datos",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta de Almacenamiento
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Almacenamiento Mapas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("385 MB usados / 48 GB libres", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { 0.08f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = ColorNeonGreen,
                    trackColor = Color(0xFF1E293B),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "REGIONES DISPONIBLES (${mapPackages.size})",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mapPackages) { pkg ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pkg.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = pkg.description,
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pkg.sizeMb} MB",
                                color = ColorNeonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (pkg.isDownloading) {
                            Column {
                                Text("Descargando mapa...", color = ColorNeonGreen, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = ColorNeonGreen,
                                    trackColor = Color(0xFF1E293B)
                                )
                            }
                        } else if (pkg.isDownloaded) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓ Descargado y listo offline", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { pkg.isDownloaded = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.Red),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Eliminar", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    pkg.isDownloading = true
                                    // Simulación de descarga rápida
                                    pkg.isDownloaded = true
                                    pkg.isDownloading = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeonGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Text("⬇ DESCARGAR MAPA OFFLINE", color = ColorNeonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
