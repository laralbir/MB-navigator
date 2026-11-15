package org.mbnavigator.android.ui.maps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mbnavigator.android.core.maps.ActiveNetworkType
import org.mbnavigator.android.core.maps.MapPackageState
import org.mbnavigator.android.core.maps.OfflineMapManager
import org.mbnavigator.android.core.maps.WorldwideMapRegion
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.rememberNavigatorPalette

@Composable
fun OfflineMapsScreen(
    onBackToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    offlineMapManager: OfflineMapManager = rememberOfflineMapManager()
) {
    val palette = rememberNavigatorPalette()
    val catalog = remember { offlineMapManager.getAvailableCatalog() }
    val packageStates by offlineMapManager.packageStates.collectAsState()
    val storageStats by offlineMapManager.storageStats.collectAsState()
    val networkWarning by offlineMapManager.networkWarning.collectAsState()
    val updateMessage by offlineMapManager.lastUpdateCheckMessage.collectAsState()
    val wifiOnly by AppPreferencesManager.wifiOnlyDownloads.collectAsState()

    var selectedContinent by remember { mutableStateOf<String?>(null) }
    var selectedCountry by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val activeNetType = offlineMapManager.getActiveNetworkType()
    val outdatedPackages = catalog.filter { reg ->
        val st = packageStates[reg.id] as? MapPackageState.Downloaded
        st != null && st.installedVersion != reg.latestVersion
    }

    val continentOrder = listOf("Europa", "América del Norte", "América del Sur", "Asia", "Oceanía", "África", "Global")
    val continentsList = remember(catalog) {
        continentOrder.filter { cont -> catalog.any { it.continent == cont } }
    }

    val countriesInContinent = remember(selectedContinent, catalog) {
        if (selectedContinent == null) emptyList()
        else catalog.filter { it.continent == selectedContinent }.map { it.country }.distinct()
    }

    val regionsInSelection = remember(selectedContinent, selectedCountry, searchQuery, catalog) {
        when {
            searchQuery.isNotBlank() -> catalog.filter {
                it.regionName.contains(searchQuery, ignoreCase = true) ||
                    it.country.contains(searchQuery, ignoreCase = true) ||
                    it.continent.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
            }
            selectedCountry != null -> catalog.filter {
                it.continent == selectedContinent && it.country == selectedCountry
            }
            else -> emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Barra superior con botón volver a Ajustes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .height(44.dp)
                    .clickable {
                        when {
                            selectedCountry != null -> selectedCountry = null
                            selectedContinent != null -> selectedContinent = null
                            else -> onBackToSettings()
                        }
                    },
                color = palette.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedContinent == null && selectedCountry == null) "← Ajustes" else "← Atrás",
                        color = palette.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mapas Offline y Regiones",
                    color = palette.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val breadcrumb = buildList {
                    add("Mundo")
                    selectedContinent?.let { add(it) }
                    selectedCountry?.let { add(it) }
                }.joinToString(" › ")
                Text(
                    text = breadcrumb,
                    color = palette.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Panel de Red, Control Wi-Fi y Versiones / Almacenamiento
        Card(
            colors = CardDefaults.cardColors(containerColor = palette.surface),
            border = BorderStroke(1.dp, palette.border),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Almacenamiento: ${storageStats.occupiedText} usados",
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Libres: ${storageStats.freeText} · Red: ${
                                when (activeNetType) {
                                    ActiveNetworkType.WIFI -> "📶 Wi-Fi"
                                    ActiveNetworkType.CELLULAR -> "📱 Datos móviles"
                                    ActiveNetworkType.OFFLINE -> "⚠️ Sin conexión"
                                    ActiveNetworkType.UNKNOWN -> "🌐 Conectado"
                                }
                            }",
                            color = palette.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Solo Wi-Fi",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { AppPreferencesManager.setWifiOnlyDownloads(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = palette.onPrimary,
                                checkedTrackColor = palette.primary
                            )
                        )
                    }
                }

                // Control de versiones de mapas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { offlineMapManager.checkForUpdates() },
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "🔄 Buscar actualizaciones",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (outdatedPackages.isNotEmpty()) {
                        Button(
                            onClick = { offlineMapManager.updateAllOutdated() },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Text(
                                text = "⬆ Actualizar (${outdatedPackages.size})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                updateMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = palette.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Aviso si se bloqueó descarga por regla Solo Wi-Fi en datos móviles
        networkWarning?.let { warn ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = palette.secondary.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, palette.secondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(warn, color = palette.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                AppPreferencesManager.setWifiOnlyDownloads(false)
                                offlineMapManager.dismissNetworkWarning()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Permitir datos móviles", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { offlineMapManager.dismissNetworkWarning() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cerrar", color = palette.textPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Buscador rápido transversal
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar país o región (ej: Valencia, Alpes, España)...", fontSize = 13.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary,
                focusedBorderColor = palette.primary,
                unfocusedBorderColor = palette.border
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Contenido jerárquico: Continentes -> Países -> Regiones
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                searchQuery.isNotBlank() || selectedCountry != null -> {
                    if (selectedCountry != null && searchQuery.isBlank()) {
                        val countryRegions = regionsInSelection
                        val totalCountryMb = countryRegions.sumOf { it.sizeMb }
                        item(key = "download_country_banner") {
                            Button(
                                onClick = { offlineMapManager.downloadCountry(selectedCountry!!) },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "⬇ Descargar ${selectedCountry} completo (${countryRegions.size} regiones · $totalCountryMb MB)",
                                    color = palette.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(regionsInSelection, key = { it.id }) { region ->
                        RegionPackageCard(
                            region = region,
                            state = packageStates[region.id] ?: MapPackageState.NotDownloaded,
                            offlineMapManager = offlineMapManager
                        )
                    }
                }

                selectedContinent != null -> {
                    val continentRegions = catalog.filter { it.continent == selectedContinent }
                    val totalContMb = continentRegions.sumOf { it.sizeMb }
                    item(key = "download_continent_banner") {
                        Button(
                            onClick = { offlineMapManager.downloadContinent(selectedContinent!!) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "⬇ Descargar todo $selectedContinent (${continentRegions.size} paquetes · $totalContMb MB)",
                                color = palette.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    items(countriesInContinent, key = { it }) { country ->
                        val regs = catalog.filter { it.continent == selectedContinent && it.country == country }
                        val downloadedCount = regs.count { packageStates[it.id] is MapPackageState.Downloaded }
                        val totalMb = regs.sumOf { it.sizeMb }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface),
                            border = BorderStroke(1.dp, palette.border),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCountry = country }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = country,
                                        color = palette.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${regs.size} región(es) · $totalMb MB · $downloadedCount instaladas",
                                        color = palette.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = { offlineMapManager.downloadCountry(country) },
                                        border = BorderStroke(1.dp, palette.primary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("⬇ Todo", color = palette.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("›", color = palette.textSecondary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Nivel 1: Continentes
                    items(continentsList, key = { it }) { continent ->
                        val regs = catalog.filter { it.continent == continent }
                        val countriesCount = regs.map { it.country }.distinct().size
                        val downloadedCount = regs.count { packageStates[it.id] is MapPackageState.Downloaded }
                        val icon = when (continent) {
                            "Europa" -> "🇪🇺"
                            "América del Norte" -> "🌎"
                            "América del Sur" -> "🌎"
                            "Asia" -> "🌏"
                            "Oceanía" -> "🏝️"
                            "África" -> "🌍"
                            else -> "🌐"
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = palette.surface),
                            border = BorderStroke(1.dp, palette.border),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedContinent = continent }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = icon, fontSize = 26.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = continent,
                                            color = palette.textPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "$countriesCount países · ${regs.size} regiones · $downloadedCount descargadas",
                                            color = palette.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Text("›", color = palette.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionPackageCard(
    region: WorldwideMapRegion,
    state: MapPackageState,
    offlineMapManager: OfflineMapManager
) {
    val palette = rememberNavigatorPalette()

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = region.regionName,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${region.country} · ${region.description}",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Chip de versión
                val versionLabel = when (state) {
                    is MapPackageState.Downloaded -> {
                        if (state.installedVersion != region.latestVersion) {
                            "v${state.installedVersion} → v${region.latestVersion}"
                        } else {
                            "v${state.installedVersion}"
                        }
                    }
                    else -> "v${region.latestVersion}"
                }
                val isOutdated = state is MapPackageState.Downloaded && state.installedVersion != region.latestVersion
                Surface(
                    color = if (isOutdated) palette.secondary.copy(alpha = 0.16f) else palette.surfaceAlt,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = versionLabel,
                        color = if (isOutdated) palette.secondary else palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (state) {
                is MapPackageState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = palette.primary,
                        trackColor = palette.surfaceAlt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.speedText, color = palette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { offlineMapManager.cancelDownload(region.id) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancelar", color = palette.danger, fontSize = 11.sp)
                        }
                    }
                }

                is MapPackageState.Downloaded -> {
                    val hasUpdate = state.installedVersion != region.latestVersion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (hasUpdate) "⬆ Actualización lista" else "✅ Instalado (${region.sizeMb} MB)",
                            color = if (hasUpdate) palette.secondary else palette.success,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasUpdate) {
                            Button(
                                onClick = { offlineMapManager.startDownload(region.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.secondary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Actualizar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (region.id != "world-base") {
                            OutlinedButton(
                                onClick = { offlineMapManager.deletePackage(region.id) },
                                border = BorderStroke(1.dp, palette.danger),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Borrar", color = palette.danger, fontSize = 12.sp)
                            }
                        }
                    }
                }

                is MapPackageState.Error -> {
                    Text(state.message, color = palette.danger, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { offlineMapManager.startDownload(region.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reintentar (${region.sizeMb} MB)", color = palette.onPrimary, fontSize = 12.sp)
                    }
                }

                MapPackageState.NotDownloaded -> {
                    Button(
                        onClick = { offlineMapManager.startDownload(region.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceAlt),
                        border = BorderStroke(1.dp, palette.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text(
                            text = "⬇ Descargar región (${region.sizeMb} MB)",
                            color = palette.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rememberOfflineMapManager(): OfflineMapManager {
    val context = LocalContext.current
    return remember(context) {
        OfflineMapManager(context.applicationContext)
    }
}
