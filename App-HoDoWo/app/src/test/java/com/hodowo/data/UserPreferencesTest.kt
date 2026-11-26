package com.hodowo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun nothingStoredGivesTheDefaults() {
        val preferences = decodePreferences(emptyMap())
        assertEquals(UserPreferences(), preferences)
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertEquals(1f, preferences.fontScale)
        assertEquals(ColourVisionMode.NONE, preferences.colourVisionMode)
        assertEquals("", preferences.languageTag)
    }

    @Test
    fun everyChoiceIsReadBackUnchanged() {
        val chosen = UserPreferences(
            themeMode = ThemeMode.DARK,
            dynamicColour = true,
            fontScale = 1.3f,
            colourVisionMode = ColourVisionMode.DEUTERANOPIA,
            colourTint = 0xFF2196F3.toInt(),
            tintStrength = 0.6f,
            highContrast = true,
            reduceMotion = true,
            languageTag = "es"
        )
        assertEquals(chosen, decodePreferences(encodePreferences(chosen)))
    }

    @Test
    fun unknownOrWronglyTypedValuesFallBackToTheDefaults() {
        val stored = mapOf(
            PreferenceKeys.THEME_MODE to "PURPLE",
            PreferenceKeys.DYNAMIC_COLOUR to "yes",
            PreferenceKeys.COLOUR_VISION_MODE to 3,
            PreferenceKeys.COLOUR_TINT to "red",
            PreferenceKeys.HIGH_CONTRAST to 1,
            PreferenceKeys.LANGUAGE_TAG to "not a language!"
        )
        assertEquals(UserPreferences(), decodePreferences(stored))
    }

    @Test
    fun outOfRangeNumbersAreBroughtBackIntoRange() {
        val tooBig = decodePreferences(
            mapOf(PreferenceKeys.FONT_SCALE to 9f, PreferenceKeys.TINT_STRENGTH to 4f)
        )
        assertEquals(UserPreferences.MAX_FONT_SCALE, tooBig.fontScale)
        assertEquals(1f, tooBig.tintStrength)

        val tooSmall = decodePreferences(
            mapOf(PreferenceKeys.FONT_SCALE to 0.1f, PreferenceKeys.TINT_STRENGTH to -1f)
        )
        assertEquals(UserPreferences.MIN_FONT_SCALE, tooSmall.fontScale)
        assertEquals(0f, tooSmall.tintStrength)

        assertEquals(1f, decodePreferences(mapOf(PreferenceKeys.FONT_SCALE to Float.NaN)).fontScale)
    }

    @Test
    fun languageTagsWithARegionAreKept() {
        assertEquals("pt-BR", decodePreferences(mapOf(PreferenceKeys.LANGUAGE_TAG to "pt-BR")).languageTag)
        assertEquals("ca", decodePreferences(mapOf(PreferenceKeys.LANGUAGE_TAG to "ca")).languageTag)
    }
}

class ThemeResolutionTest {

    @Test
    fun followingTheSystemUsesItsSetting() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun aManualChoiceIgnoresTheSystem() {
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun theAppScaleMultipliesTheSystemOne() {
        assertEquals(1.3f, appFontScale(systemScale = 1f, chosenScale = 1.3f), 1e-6f)
        assertEquals(1.5f, appFontScale(systemScale = 1.5f, chosenScale = 1f), 1e-6f)
        assertEquals(1.8f, appFontScale(systemScale = 1.2f, chosenScale = 1.5f), 1e-6f)
    }

    @Test
    fun theAppScaleIsCapped() {
        assertEquals(MAX_APP_FONT_SCALE, appFontScale(systemScale = 2f, chosenScale = 2f), 1e-6f)
    }
}

class AppLanguageTagTest {

    @Test
    fun theFirstSupportedLanguageIsUsed() {
        assertEquals("it", appLanguageTag("it-IT"))
        assertEquals("es", appLanguageTag("es-ES,en-US"))
        assertEquals("ca", appLanguageTag("ca"))
        assertEquals("en", appLanguageTag("EN_gb"))
    }

    @Test
    fun noOrAnUnsupportedLanguageFollowsTheSystem() {
        assertEquals("", appLanguageTag(""))
        assertEquals("", appLanguageTag("fr-FR"))
    }
}
