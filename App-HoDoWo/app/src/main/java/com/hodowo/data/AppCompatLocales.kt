package com.hodowo.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
