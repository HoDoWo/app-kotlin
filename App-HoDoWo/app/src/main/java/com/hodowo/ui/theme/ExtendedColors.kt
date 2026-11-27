package com.hodowo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Colours Material 3 has no role for (#214): success, ratings and the kinds of control
 * on a panel. Each has a light and a dark value, picked from the current surface.
 */

private const val DARK_SURFACE_LUMINANCE = 0.5f

/** Whether the current scheme is dark (also with dynamic colour or high contrast). */
val isDarkScheme: Boolean
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE

/** "It fits", "In stock" and the like. */
val Success: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFF7DDC93) else Color(0xFF1B8A3A)

/** Filled rating stars. */
val Rating: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFFFFC14D) else Color(0xFFB86E00)

/** Panel controls by kind (panel overlay legend). */
val ControlButton: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFF90CAF9) else Color(0xFF1976D2)
val ControlDial: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFFFFB74D) else Color(0xFFE65100)
val ControlSwitch: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFFCE93D8) else Color(0xFF8E24AA)
val ControlDisplay: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
val ControlOther: Color
    @Composable @ReadOnlyComposable get() = if (isDarkScheme) Color(0xFFBDBDBD) else Color(0xFF757575)
