package com.hodowo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text on every background of the four schemes is readable (#214): WCAG AA (4.5:1)
 * in the normal schemes, AAA (7:1) in the high-contrast ones.
 */
class ColorSchemeContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun textPairs(s: ColorScheme) = mapOf(
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onSurface/surface" to (s.onSurface to s.surface),
        "onBackground/background" to (s.onBackground to s.background),
        "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        "onError/error" to (s.onError to s.error),
        "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
        "primary/surface" to (s.primary to s.surface),
        "error/surface" to (s.error to s.surface)
    )

    private fun assertReadable(name: String, scheme: ColorScheme, minimum: Float) {
        val failing = textPairs(scheme).mapNotNull { (pair, colours) ->
            val ratio = contrast(colours.first, colours.second)
            if (ratio < minimum) "$pair = %.2f".format(ratio) else null
        }
        assertTrue("$name below $minimum:1: $failing", failing.isEmpty())
    }

    @Test
    fun lightAndDarkReachAA() {
        assertReadable("light", colorSchemeFor(dark = false, highContrast = false), AA)
        assertReadable("dark", colorSchemeFor(dark = true, highContrast = false), AA)
    }

    @Test
    fun highContrastReachesAAA() {
        assertReadable("high contrast", colorSchemeFor(dark = false, highContrast = true), AAA)
        assertReadable("high contrast dark", colorSchemeFor(dark = true, highContrast = true), AAA)
    }

    @Test
    fun outlinesAreVisibleOnTheSurface() {
        listOf(false, true).forEach { dark ->
            listOf(false, true).forEach { high ->
                val s = colorSchemeFor(dark, high)
                assertTrue("outline dark=$dark high=$high", contrast(s.outline, s.surface) >= NON_TEXT)
            }
        }
    }

    @Test
    fun theDarkSchemeIsReallyDark() {
        assertTrue(colorSchemeFor(dark = true, highContrast = false).surface.luminance() < 0.05f)
        assertTrue(colorSchemeFor(dark = false, highContrast = false).surface.luminance() > 0.8f)
    }

    private companion object {
        const val AA = 4.5f
        const val AAA = 7f
        const val NON_TEXT = 3f
    }
}
