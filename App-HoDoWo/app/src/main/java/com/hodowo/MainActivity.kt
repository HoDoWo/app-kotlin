package com.hodowo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import com.hodowo.data.AppLanguage
import com.hodowo.data.ArSupportProvider
import com.hodowo.ui.HoDoWoApp
import com.hodowo.data.isDark
import com.hodowo.ui.theme.HoDoWoTheme
import com.hodowo.viewmodel.AppPreferencesViewModel
import kotlinx.coroutines.launch

// AppCompatActivity: it applies the per-app language below Android 13 too (#235).
class MainActivity : AppCompatActivity() {

    // Theme, text size and the other appearance choices (#211, #212).
    private val preferencesViewModel: AppPreferencesViewModel by viewModels {
        AppPreferencesViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Guide images are kept in Coil's disk cache even when the server says not to
        // cache them, so the Home lists and the chats can be looked at offline.
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .respectCacheHeaders(false)
                .build()
        )
        // Ask ARCore early (#239): its first answer can take a moment, and the
        // AR tab should already know whether this phone supports AR.
        lifecycleScope.launch { ArSupportProvider.get(applicationContext).refresh() }
        // The language may have been changed in the system settings (Android 13+).
        preferencesViewModel.syncLanguage(AppLanguage.current())
        setContent {
            val preferences by preferencesViewModel.preferences.collectAsState()
            // Nothing is drawn until the stored choices are read (a few ms): the first
            // frame already has the right theme and text size, with no flash.
            preferences?.let { chosen ->
                HoDoWoTheme(chosen) {
                    SystemBarsAndWindow(dark = chosen.themeMode.isDark(isSystemInDarkTheme()))
                    // Chat + camera flow (#38, #64).
                    // SkeletonScreen (#49) stays in the code base but is no longer
                    // the start screen: its backend client will be plugged into
                    // ChatViewModel in a follow-up PR.
                    HoDoWoApp()
                }
            }
        }
    }

    /**
     * Status and navigation bar icons readable on the app's theme, not the system's,
     * and the window behind the app painted with the theme background (no white
     * block in dark mode while a screen loads).
     */
    @Composable
    private fun SystemBarsAndWindow(dark: Boolean) {
        val background = MaterialTheme.colorScheme.background.toArgb()
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark }
            )
            onDispose {}
        }
        SideEffect { window.decorView.setBackgroundColor(background) }
    }
}

/** The navigation bar scrims enableEdgeToEdge uses by default. */
private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
