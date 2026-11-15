package org.mbnavigator.android.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import org.mbnavigator.android.ui.navigation.MotorcycleNavigationScreen

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Pantalla de Navegación Principal para Motos
    MotorcycleNavigationScreen(modifier = modifier.fillMaxSize())
}
