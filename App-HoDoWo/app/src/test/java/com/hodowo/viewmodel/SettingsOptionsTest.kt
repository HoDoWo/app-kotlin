package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsOptionsTest {

    @Test
    fun storedScalesMatchTheirOption() {
        assertEquals(TextSizeOption.DEFAULT, textSizeOptionFor(1f))
        assertEquals(TextSizeOption.LARGE, textSizeOptionFor(1.15f))
        assertEquals(TextSizeOption.SMALL, textSizeOptionFor(0.85f))
    }

    @Test
    fun aCustomScaleHasNoOptionAndShowsAsAPercentage() {
        assertNull(textSizeOptionFor(1.2f))
        assertEquals(120, textSizePercent(1.2f))
        assertEquals(85, textSizePercent(0.85f))
    }

    @Test
    fun languagesAreFoundByTag() {
        assertEquals("Italiano", languageOptionFor("it").nativeName)
        assertEquals("Català", languageOptionFor("ca").nativeName)
    }

    @Test
    fun theSystemLanguageComesFirstAndCatchesUnknownTags() {
        assertEquals("", APP_LANGUAGES.first().tag)
        assertEquals("", languageOptionFor("").tag)
        assertEquals("", languageOptionFor("xx").tag)
    }
}
