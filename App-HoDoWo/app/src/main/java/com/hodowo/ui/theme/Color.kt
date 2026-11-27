package com.hodowo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/*
 * HoDoWo colours (#214).
 *
 * The screens use the names below (Primary, Surface, OnSurfaceVariant…). They read the
 * current theme, so every screen follows light, dark and high contrast without changes.
 * The raw values of each scheme are in Palette.kt; nothing outside this package
 * writes a Color(0x…) value.
 */

val Primary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val OnPrimary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimary
val PrimaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
val OnPrimaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer

val Secondary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
val OnSecondary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSecondary
val SecondaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondaryContainer
val OnSecondaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSecondaryContainer

val Tertiary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val OnTertiary: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onTertiary
val TertiaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiaryContainer
val OnTertiaryContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onTertiaryContainer

val Background: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val OnBackground: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground
val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val OnSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val OnSurfaceVariant: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

val SurfaceContainerLowest: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLowest
val SurfaceContainerLow: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLow
val SurfaceContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
val SurfaceContainerHigh: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val SurfaceContainerHighest: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHighest

val InverseSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inverseSurface
val InverseOnSurface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inverseOnSurface

val Error: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val OnError: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onError
val ErrorContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.errorContainer
val OnErrorContainer: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onErrorContainer

val Outline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
val OutlineVariant: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/*
 * Soft tints for selected rows, chips and pills. The app uses them behind normal text
 * (OnSurface, Primary), so unlike Material's fixed colours they follow the theme:
 * pale in light, deep in dark.
 */
val PrimaryFixed: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFF33296E) else Color(0xFFE5DEFF)
val PrimaryFixedDim: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFF4126C9) else Color(0xFFC8BFFF)
val SecondaryFixed: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFF2E3549) else Color(0xFFDAE2FD)

/*
 * Amber tags (second-hand, pending): the same in light and dark, always paired with
 * OnTertiaryFixed (Material 3 "fixed" colours).
 */
val TertiaryFixed = Color(0xFFFFDDB8)
val TertiaryFixedDim = Color(0xFFFFB95F)
val OnTertiaryFixed = Color(0xFF2A1700)

/*
 * Over photos and the camera the colours do not follow the theme: the picture is the
 * background, so pins, labels and controls keep the brand purple with white text and
 * a dark scrim, which read well on any image.
 */
val OverlayBrand = Color(0xFF583AE2)
val OverlayBrandLight = Color(0xFF7258FC)
val OnOverlay = Color(0xFFFFFFFF)
val OverlayScrim = Color(0x99000000)
val MediaBackground = Color(0xFF000000)

/** Brand header gradient (login, search, orders): deep purples with white text in every theme. */
val BrandDeep = Color(0xFF26008C)
val BrandDeepContainer = Color(0xFF3B02C7)
