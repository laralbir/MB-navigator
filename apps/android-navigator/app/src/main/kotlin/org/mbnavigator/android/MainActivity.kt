package org.mbnavigator.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.AppThemeMode
import org.mbnavigator.android.ui.theme.MBNavigatorTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_MBNavigator)
    super.onCreate(savedInstanceState)

    AppPreferencesManager.init(applicationContext)
    enableEdgeToEdge()

    setContent {
      val mode by AppPreferencesManager.themeMode.collectAsState()
      val systemDark = isSystemInDarkTheme()
      val isDark = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.AUTO -> systemDark
      }

      LaunchedEffect(isDark) {
        val barStyle = if (isDark) {
          SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
          )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
      }

      MBNavigatorTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          MainNavigation()
        }
      }
    }
  }
}
