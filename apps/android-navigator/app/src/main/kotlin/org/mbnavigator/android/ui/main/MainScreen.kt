package org.mbnavigator.android.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.rememberNavigatorPalette
import org.mbnavigator.android.ui.gps.GpsSatellitesScreen
import org.mbnavigator.android.ui.navigation.MotorcycleNavigationScreen
import org.mbnavigator.android.ui.routes.RoutesScreen
import org.mbnavigator.android.ui.settings.SettingsScreen

enum class AppTab(val title: String, val icon: String) {
    NAVIGATOR("Navegar", "🧭"),
    ROUTES("Rutas", "📂"),
    GPS_SIGNAL("GPS", "📡"),
    SETTINGS("Ajustes", "⚙️")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AppPreferencesManager.init(context)
    }

    val palette = rememberNavigatorPalette()
    var selectedTab by remember { mutableStateOf(AppTab.NAVIGATOR) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg),
        containerColor = palette.bg,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                containerColor = palette.surface,
                contentColor = palette.textPrimary,
                tonalElevation = 8.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Text(
                                text = tab.icon,
                                fontSize = if (isSelected) 22.sp else 18.sp
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) palette.primary else palette.textSecondary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.primary,
                            selectedTextColor = palette.primary,
                            indicatorColor = palette.primary.copy(alpha = 0.14f),
                            unselectedIconColor = palette.textSecondary,
                            unselectedTextColor = palette.textSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                AppTab.NAVIGATOR -> MotorcycleNavigationScreen(
                    onOpenGpsDiagnostics = { selectedTab = AppTab.GPS_SIGNAL },
                    onOpenRoutes = { selectedTab = AppTab.ROUTES },
                    modifier = Modifier.fillMaxSize()
                )
                AppTab.ROUTES -> RoutesScreen(
                    onSelectRouteToNavigate = {
                        selectedTab = AppTab.NAVIGATOR
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                )
                AppTab.GPS_SIGNAL -> GpsSatellitesScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                )
            }
        }
    }
}
