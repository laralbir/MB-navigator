package org.mbnavigator.android.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.routing.NavigationRunState
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.SystemNavBarMode
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
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        AppPreferencesManager.init(context)
    }

    val tracker = remember { RealLocationTracker.getInstance(context) }
    val currentLocation by tracker.currentLocation.collectAsState()
    val palette = rememberNavigatorPalette(currentLocation)

    val navigationState by NavigationSessionRepository.navigationState.collectAsState()
    val activeRoute by NavigationSessionRepository.activeRoute.collectAsState()
    val sharedImportEvent by NavigationSessionRepository.sharedImportEvent.collectAsState()
    val sysNavBarMode by AppPreferencesManager.systemNavBarMode.collectAsState()

    var selectedTab by remember { mutableStateOf(AppTab.NAVIGATOR) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(sharedImportEvent?.timestampMs) {
        val ev = sharedImportEvent ?: return@LaunchedEffect
        if (ev.success) {
            selectedTab = AppTab.NAVIGATOR
        }
        delay(4500L)
        NavigationSessionRepository.consumeSharedImportEvent()
    }

    val isTripActiveInMap = selectedTab == AppTab.NAVIGATOR &&
        (navigationState == NavigationRunState.NAVIGATING || navigationState == NavigationRunState.PAUSED)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = palette.surface,
                drawerContentColor = palette.textPrimary
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    Text(
                        text = "MB-navigator",
                        color = palette.primary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = activeRoute?.title ?: "Menú de navegación",
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    )
                }
                HorizontalDivider(color = palette.border)
                Spacer(modifier = Modifier.height(8.dp))

                AppTab.entries.forEach { tab ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = "${tab.icon}  ${tab.title}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        },
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = palette.primary.copy(alpha = 0.14f),
                            selectedTextColor = palette.primary,
                            unselectedTextColor = palette.textPrimary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(palette.bg),
            containerColor = palette.bg,
            bottomBar = {
                if (!isTripActiveInMap) {
                    Surface(
                        color = palette.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
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
                } else if (sysNavBarMode == SystemNavBarMode.ALWAYS_VISIBLE) {
                    // Garantiza que la barra inferior del sistema conserve fondo sólido mientras se mueve el mapa
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(palette.surface)
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bg)
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .clipToBounds()
            ) {
                when (selectedTab) {
                    AppTab.NAVIGATOR -> MotorcycleNavigationScreen(
                        showHamburgerMenu = isTripActiveInMap,
                        onOpenHamburgerMenu = { scope.launch { drawerState.open() } },
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

                sharedImportEvent?.let { ev ->
                    Surface(
                        color = palette.hudOverlay,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (ev.success) palette.success else palette.danger
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (ev.success) "✓ " else "⚠️ ") + ev.message,
                                color = if (ev.success) palette.success else palette.danger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { NavigationSessionRepository.consumeSharedImportEvent() }) {
                                Text("OK", color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
