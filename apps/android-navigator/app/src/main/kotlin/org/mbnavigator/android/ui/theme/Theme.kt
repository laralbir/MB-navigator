package org.mbnavigator.android.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.settings.rememberIsDarkModeActive

private val DarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = Color(0xFF00354A),
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = Color(0xFFCFEAFD),
    secondary = secondaryDark,
    onSecondary = Color(0xFF193240),
    secondaryContainer = Color(0xFF314959),
    onSecondaryContainer = Color(0xFFCFEAFD),
    tertiary = tertiaryDark,
    onTertiary = Color(0xFF331445),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF40484E),
    onSurfaceVariant = Color(0xFFBFC8CF),
    outline = Color(0xFF899298),
    outlineVariant = Color(0xFF40484E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = secondaryLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFEAFD),
    onSecondaryContainer = Color(0xFF344D5C),
    tertiary = tertiaryLight,
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FD),
    onBackground = Color(0xFF191C1F),
    surface = Color(0xFFF7F9FD),
    onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFDBE4EB),
    onSurfaceVariant = Color(0xFF40484E),
    outline = Color(0xFF70787E),
    outlineVariant = Color(0xFFBFC8CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MBNavigatorTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val tracker = remember(context) { RealLocationTracker.getInstance(context) }
    val currentLocation by tracker.currentLocation.collectAsState()
    val darkTheme = rememberIsDarkModeActive(currentLocation)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
