package com.hodowo.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun portraitPhoneKeepsTheCompactLayout() {
        val info = appLayoutInfoFor(width = 411.dp, isLandscape = false)

        assertEquals(AppWidthClass.COMPACT, info.widthClass)
        assertFalse(info.isWide)
        assertFalse(info.useNavigationRail)
    }

    @Test
    fun phoneInLandscapeGetsTheRailAndSideBySideCards() {
        val info = appLayoutInfoFor(width = 800.dp, isLandscape = true)

        assertEquals(AppWidthClass.MEDIUM, info.widthClass)
        assertTrue(info.isWide)
        assertTrue(info.useNavigationRail)
    }

    @Test
    fun aSmallTabletInPortraitAlsoUsesTheRail() {
        val info = appLayoutInfoFor(width = 700.dp, isLandscape = false)

        assertEquals(AppWidthClass.MEDIUM, info.widthClass)
        assertTrue(info.isWide)
        assertTrue(info.useNavigationRail)
    }

    @Test
    fun largeTabletsAreExpanded() {
        val info = appLayoutInfoFor(width = 1200.dp, isLandscape = false)

        assertEquals(AppWidthClass.EXPANDED, info.widthClass)
        assertTrue(info.isWide)
        assertTrue(info.useNavigationRail)
    }
}
