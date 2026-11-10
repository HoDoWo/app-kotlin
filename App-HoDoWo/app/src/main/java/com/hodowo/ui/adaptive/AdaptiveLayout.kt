package com.hodowo.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much horizontal room a screen has.
 *
 * The breakpoints follow Material 3: phones are [COMPACT], small tablets and
 * phones in landscape are [MEDIUM], and large tablets are [EXPANDED].
 */
enum class AppWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Window size shared by every screen of the app. */
data class AppLayoutInfo(
    val widthClass: AppWidthClass,
    val isLandscape: Boolean
) {
    /**
     * True when there is enough room for a side-by-side layout (image next to
     * text). A phone in portrait keeps the compact, stacked layout.
     */
    val isWide: Boolean get() = widthClass != AppWidthClass.COMPACT

    /** Wide enough to move the bottom tabs into a vertical navigation rail. */
    val useNavigationRail: Boolean get() = widthClass != AppWidthClass.COMPACT
}

/** Default used by previews/tests, which have no real window. */
val CompactAppLayout = AppLayoutInfo(AppWidthClass.COMPACT, isLandscape = false)

/** The current window size, provided once by [com.hodowo.ui.HoDoWoApp]. */
val LocalAppLayout = staticCompositionLocalOf { CompactAppLayout }

/** Computes the layout information for an available width in dp. */
fun appLayoutInfoFor(width: Dp, isLandscape: Boolean): AppLayoutInfo {
    val widthClass = when {
        width < 600.dp -> AppWidthClass.COMPACT
        width < 840.dp -> AppWidthClass.MEDIUM
        else -> AppWidthClass.EXPANDED
    }
    return AppLayoutInfo(widthClass = widthClass, isLandscape = isLandscape)
}

/**
 * Keeps a screen's scrollable content readable on tablets and in landscape.
 *
 * The content is centred and never grows past [maxWidth]; on phones it simply
 * fills the available width. Screens wrap their list/column in this instead of
 * letting text and cards stretch across the whole display.
 */
@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 1000.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content
        )
    }
}
