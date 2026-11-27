package com.hodowo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The four colour schemes of the app (#214), every Material 3 role defined.
 * Light keeps the brand purple of the mock-ups; dark uses its light tone (like Material 3
 * does) so purple text and buttons stay readable on dark surfaces; the high-contrast
 * schemes (#213) push text and outlines to the extremes.
 */

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF583AE2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7258FC),
    onPrimaryContainer = Color(0xFFFFFBFF),
    inversePrimary = Color(0xFFC8BFFF),
    secondary = Color(0xFF565E74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2FD),
    onSecondaryContainer = Color(0xFF5C647A),
    tertiary = Color(0xFF825100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA36700),
    onTertiaryContainer = Color(0xFFFFFBFF),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF0B1C30),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF0B1C30),
    surfaceVariant = Color(0xFFD3E4FE),
    onSurfaceVariant = Color(0xFF3F4850),
    surfaceTint = Color(0xFF583AE2),
    inverseSurface = Color(0xFF213145),
    inverseOnSurface = Color(0xFFEAF1FF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF707881),
    outlineVariant = Color(0xFFBFC7D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceDim = Color(0xFFCBDBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4FF),
    surfaceContainer = Color(0xFFE5EEFF),
    surfaceContainerHigh = Color(0xFFDCE9FF),
    surfaceContainerHighest = Color(0xFFD3E4FE)
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2B0E8F),
    primaryContainer = Color(0xFF4126C9),
    onPrimaryContainer = Color(0xFFE5DEFF),
    inversePrimary = Color(0xFF583AE2),
    secondary = Color(0xFFBEC6E0),
    onSecondary = Color(0xFF283044),
    secondaryContainer = Color(0xFF3E465C),
    onSecondaryContainer = Color(0xFFDAE2FD),
    tertiary = Color(0xFFFFB95F),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF0E141B),
    onBackground = Color(0xFFDDE3EE),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFDDE3EE),
    surfaceVariant = Color(0xFF3F4850),
    onSurfaceVariant = Color(0xFFBFC7D2),
    surfaceTint = Color(0xFFC8BFFF),
    inverseSurface = Color(0xFFDDE3EE),
    inverseOnSurface = Color(0xFF2B3138),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF89929C),
    outlineVariant = Color(0xFF3F4850),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343A41),
    surfaceDim = Color(0xFF0E141B),
    surfaceContainerLowest = Color(0xFF090F15),
    surfaceContainerLow = Color(0xFF161C23),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF252B32),
    surfaceContainerHighest = Color(0xFF30363D)
)

internal val LightHighContrastColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2E0F9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4126C9),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFE5DEFF),
    secondary = Color(0xFF242C40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF3E465C),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF3D2400),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE4EAF4),
    onSurfaceVariant = Color(0xFF1B232B),
    surfaceTint = Color(0xFF2E0F9E),
    inverseSurface = Color(0xFF000000),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFF600004),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF98000A),
    onErrorContainer = Color(0xFFFFFFFF),
    outline = Color(0xFF242C35),
    outlineVariant = Color(0xFF3A434D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD5DCE6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6FA),
    surfaceContainer = Color(0xFFEDF0F5),
    surfaceContainerHigh = Color(0xFFE4E8EF),
    surfaceContainerHighest = Color(0xFFDDE2EA)
)

internal val DarkHighContrastColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFF2EEFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFC8BFFF),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF2B0E8F),
    secondary = Color(0xFFEEF1FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFBEC6E0),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFFFFEDDC),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFFFB95F),
    onTertiaryContainer = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A323A),
    onSurfaceVariant = Color(0xFFF0F4FA),
    surfaceTint = Color(0xFFF2EEFF),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFFFECE9),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFFFAEA4),
    onErrorContainer = Color(0xFF220001),
    outline = Color(0xFFE9EEF5),
    outlineVariant = Color(0xFFBCC4CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E343B),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0E13),
    surfaceContainer = Color(0xFF12171D),
    surfaceContainerHigh = Color(0xFF1B2128),
    surfaceContainerHighest = Color(0xFF242A31)
)

/** The scheme for a theme: [dark] and [highContrast] as chosen in Settings. */
internal fun colorSchemeFor(dark: Boolean, highContrast: Boolean): ColorScheme = when {
    dark && highContrast -> DarkHighContrastColors
    dark -> DarkColors
    highContrast -> LightHighContrastColors
    else -> LightColors
}
