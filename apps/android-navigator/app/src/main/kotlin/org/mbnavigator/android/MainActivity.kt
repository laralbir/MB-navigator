package org.mbnavigator.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mbnavigator.android.core.location.RealLocationTracker
import org.mbnavigator.android.core.routing.NavigationSessionRepository
import org.mbnavigator.android.core.settings.AppPreferencesManager
import org.mbnavigator.android.core.settings.SystemNavBarMode
import org.mbnavigator.android.core.settings.rememberIsDarkModeActive
import org.mbnavigator.android.ui.theme.MBNavigatorTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_MBNavigator)
    super.onCreate(savedInstanceState)

    AppPreferencesManager.init(applicationContext)
    NavigationSessionRepository.ensureInitialized(applicationContext)
    handleIncomingIntent(intent)
    enableEdgeToEdge()

    setContent {
      val tracker = remember { RealLocationTracker.getInstance(applicationContext) }
      val currentLocation by tracker.currentLocation.collectAsState()
      val isDark = rememberIsDarkModeActive(currentLocation)
      val systemNavBarMode by AppPreferencesManager.systemNavBarMode.collectAsState()

      LaunchedEffect(isDark) {
        val navScrim = if (isDark) 0xFF1D2023.toInt() else 0xFFFFFFFF.toInt()
        val barStyle = if (isDark) {
          SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(
            android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT
          )
        }
        val navStyle = if (isDark) {
          SystemBarStyle.dark(navScrim)
        } else {
          SystemBarStyle.light(navScrim, navScrim)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = navStyle)
      }

      LaunchedEffect(systemNavBarMode) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        when (systemNavBarMode) {
          SystemNavBarMode.ALWAYS_VISIBLE -> {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.show(WindowInsetsCompat.Type.navigationBars())
          }
          SystemNavBarMode.GESTURE_AUTO_HIDE -> {
            controller.systemBarsBehavior =
              WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
          }
        }
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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    val uris = mutableListOf<Uri>()
    when (intent.action) {
      Intent.ACTION_VIEW -> {
        intent.data?.let { uris.add(it) }
      }
      Intent.ACTION_SEND -> {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
          uris.add(it)
        }
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
          uris.addAll(it)
        }
      }
    }
    if (uris.isEmpty()) {
      intent.clipData?.let { clip ->
        for (i in 0 until clip.itemCount) {
          clip.getItemAt(i).uri?.let { uris.add(it) }
        }
      }
    }
    uris.distinct().forEach { uri ->
      NavigationSessionRepository.importSharedRouteUri(applicationContext, uri)
    }
  }
}
