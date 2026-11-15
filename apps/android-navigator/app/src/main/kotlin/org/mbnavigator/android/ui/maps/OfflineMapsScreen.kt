package org.mbnavigator.android.ui.maps

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.maps.MapPackageState
import org.mbnavigator.android.core.maps.OfflineMapManager
import org.mbnavigator.android.core.maps.WorldwideMapRegion

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)
private val ColorNeonAmber = Color(0xFFFF9100)
private val ColorCardBorder = Color(0xFF1E293B)

@Composable
fun OfflineMapsScreen(
    modifier: Modifier = Modifier,
    offlineMapManager: OfflineMapManager = rememberOfflineMapManager()
) {
    val context = LocalContext.current
    val continents = listOf("Todos", "Europa", "América del Norte", "América del Sur", "Asia", "África", "Oceanía")
    var selectedContinent by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    val catalog = remember { offlineMapManager.getAvailableCatalog() }
    val packageStates by offlineMapManager.packageStates.collectAsState()
    val storageStats by offlineMapManager.storageStats.collectAsState()

    val filteredMaps = remember(selectedContinent, searchQuery, catalog) {
        catalog.filter { map ->
            val matchesContinent = selectedContinent == "Todos" || map.continent == selectedContinent || map.continent == "Global"
            val matchesSearch = searchQuery.isBlank() ||
                    map.regionName.contains(searchQuery, ignoreCase = true) ||
                    map.country.contains(searchQuery, ignoreCase = true) ||
                    map.description.contains(searchQuery, ignoreCase = true)
            matchesContinent && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Cabecera
        Text(
            text = "MAPAS OFFLINE MUNDIALES",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "Descarga mapas vectoriales de cualquier país o región del mundo sin licencias comerciales",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tarjeta de almacenamiento en disco
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1826)),
            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💾", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Memoria Usada: ${storageStats.occupiedText}",
                            color = ColorNeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Espacio libre en dispositivo: ${storageStats.freeText}",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = "${packageStates.values.count { it is MapPackageState.Downloaded }} instalados",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Buscador de países o zonas
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar país, región o puerto de montaña...", color = Color.Gray, fontSize = 13.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ColorNeonGreen,
                unfocusedBorderColor = ColorCardBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Selector de Continente horizontal
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(continents) { continent ->
                val isSelected = selectedContinent == continent
                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) ColorNeonGreen else ColorSlateSurface,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) ColorNeonGreen else ColorCardBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedContinent = continent }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = continent,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "PAQUETES DISPONIBLES (${filteredMaps.size})",
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredMaps, key = { it.id }) { pkg ->
                val state = packageStates[pkg.id] ?: MapPackageState.NotDownloaded

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ColorSlateSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorCardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pkg.country.uppercase(),
                                        color = ColorNeonAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("• ${pkg.continent}", color = Color.Gray, fontSize = 10.sp)
                                }
                                Text(
                                    text = pkg.regionName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = pkg.description,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${pkg.sizeMb} MB",
                                color = ColorNeonGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        when (state) {
                            is MapPackageState.Downloaded -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("✓", color = ColorNeonGreen, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Instalado en memoria local", color = ColorNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { offlineMapManager.deletePackage(pkg.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1818), contentColor = Color(0xFFFF5252)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("🗑️ Borrar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            is MapPackageState.Downloading -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Descargando: ${state.speedText}",
                                            color = ColorNeonGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Button(
                                            onClick = { offlineMapManager.cancelDownload(pkg.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263238), contentColor = Color.LightGray),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Cancelar", fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        color = ColorNeonGreen,
                                        trackColor = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                    )
                                }
                            }

                            is MapPackageState.Error -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️ ${state.message}", color = Color.Red, fontSize = 11.sp)
                                    Button(
                                        onClick = { offlineMapManager.startDownload(pkg.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeonAmber),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Reintentar", color = ColorNeonAmber, fontSize = 11.sp)
                                    }
                                }
                            }

                            is MapPackageState.NotDownloaded -> {
                                Button(
                                    onClick = { offlineMapManager.startDownload(pkg.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorNeonGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                ) {
                                    Text(
                                        text = "⬇ DESCARGAR MAPA OFFLINE (${pkg.sizeMb} MB)",
                                        color = ColorNeonGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberOfflineMapManager(): OfflineMapManager {
    val context = LocalContext.current
    return remember(context) {
        OfflineMapManager(context.applicationContext)
    }
}
