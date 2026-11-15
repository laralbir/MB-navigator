package org.mbnavigator.android.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import org.mbnavigator.android.ui.maps.OfflineMapsScreen
import org.mbnavigator.android.ui.navigation.MotorcycleNavigationScreen
import org.mbnavigator.android.ui.routes.RoutesScreen
import org.mbnavigator.android.ui.settings.SettingsScreen

private val ColorAsphaltBg = Color(0xFF090E17)
private val ColorSlateSurface = Color(0xFF131D2E)
private val ColorNeonGreen = Color(0xFF00E676)

enum class AppTab(val title: String, val icon: String) {
    NAVIGATOR("Navegar", "🧭"),
    ROUTES("Rutas", "📂"),
    MAPS("Mapas", "🗺️"),
    SETTINGS("Ajustes", "⚙️")
}

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AppTab.NAVIGATOR) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(ColorAsphaltBg),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                containerColor = ColorSlateSurface,
                contentColor = Color.White,
                tonalElevation = 8.dp
            ) {
                AppTab.values().forEach { tab ->
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
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) ColorNeonGreen else Color.Gray
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorNeonGreen,
                            selectedTextColor = ColorNeonGreen,
                            indicatorColor = ColorNeonGreen.copy(alpha = 0.15f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                AppTab.NAVIGATOR -> MotorcycleNavigationScreen(
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
                AppTab.MAPS -> OfflineMapsScreen(
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
