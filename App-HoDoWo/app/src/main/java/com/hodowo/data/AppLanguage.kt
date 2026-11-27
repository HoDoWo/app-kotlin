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
 * Where the per-app language lives, as BCP 47 tags ("it", "" for the system language).
 * An interface so the logic of [AppLanguage] is tested without Android.
 */
interface AppLocales {
    fun get(): String
    fun set(tags: String)
}

/**
 * AppCompat per-app locales: Android 13+ stores them in the system (and shows them in
 * the app's language settings); below 13 AppCompat stores them (`autoStoreLocales`).
 * Setting them recreates the activity once with the new strings.
 */
object AppCompatLocales : AppLocales {
    override fun get(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    override fun set(tags: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tags.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tags)
        )
    }
}

/** The per-app language (#235). */
object AppLanguage {

    /** Where the language is kept; tests replace it. */
    var locales: AppLocales = AppCompatLocales

    /** Shows the app in [tag] ("" follows the system language). */
    fun apply(tag: String) {
        // Setting the same language again would recreate the activity for nothing.
        if (locales.get() != tag) locales.set(tag)
    }

    /** The language the app shows now, also when chosen in the system settings. */
    fun current(): String = appLanguageTag(locales.get())
}
