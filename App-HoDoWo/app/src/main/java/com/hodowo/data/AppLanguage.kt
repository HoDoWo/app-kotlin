package com.hodowo.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The languages the app is translated into (#169); "" means the system language. */
val SUPPORTED_LANGUAGE_TAGS = listOf("en", "it", "es", "ca")

/**
 * The app language for a locale list as Android reports it ("it-IT,en" or ""): the
 * first locale if the app supports its language, else "" (the system language).
 */
fun appLanguageTag(locales: String): String {
    val language = locales.substringBefore(',').substringBefore('-').substringBefore('_').trim().lowercase()
    return language.takeIf { it in SUPPORTED_LANGUAGE_TAGS }.orEmpty()
}

/**
 * The per-app language (#235), through AppCompat: Android 13+ stores it in the system
 * (and shows it in the app's language settings); below 13 AppCompat stores it.
 * Changing it recreates the activity once with the new strings.
 */
object AppLanguage {

    /** Shows the app in [tag] ("" follows the system language). */
    fun apply(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        // Setting the same list again does nothing, so this never loops.
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** The language the app shows now, also when chosen in the system settings. */
    fun current(): String = appLanguageTag(AppCompatDelegate.getApplicationLocales().toLanguageTags())
}
