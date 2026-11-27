package com.hodowo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.hodowo.data.UserPreferences
import com.hodowo.data.appFontScale
import com.hodowo.data.isDark

/** The app theme from the stored choices (#212): theme mode, dynamic colour and text size. */
@Composable
fun HoDoWoTheme(preferences: UserPreferences, content: @Composable () -> Unit) {
    HoDoWoTheme(
        darkTheme = preferences.themeMode.isDark(isSystemInDarkTheme()),
        dynamicColor = preferences.dynamicColour,
        highContrast = preferences.highContrast,
        fontScale = preferences.fontScale,
        content = content
    )
}

/**
 * The app theme. [fontScale] is the in-app text size: it multiplies the system's, so
 * every `sp` in the app follows it, and changes apply at once without a restart.
 * The defaults keep previews and tests as they were.
 */
@Composable
fun HoDoWoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // High contrast wins over wallpaper colours, which give no contrast guarantee.
        highContrast -> colorSchemeFor(darkTheme, highContrast = true)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorSchemeFor(darkTheme, highContrast = false)
    }

    val density = LocalDensity.current
    val scaled = Density(density.density, appFontScale(density.fontScale, fontScale))
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}