package com.hodowo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The only source of truth for appearance, accessibility and language choices (#211):
 * stored with Preferences DataStore, so they survive restarts and process death.
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    /** The current choices; a store that cannot be read gives the defaults. */
    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { stored -> decodePreferences(stored.byName()) }
        .distinctUntilChanged()

    /** Changes the stored choices with [transform]; the result is validated on read. */
    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        dataStore.edit { stored ->
            val next = transform(decodePreferences(stored.byName()))
            stored.write(next)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    suspend fun setDynamicColour(enabled: Boolean) = update { it.copy(dynamicColour = enabled) }
    suspend fun setFontScale(scale: Float) = update { it.copy(fontScale = scale) }
    suspend fun setColourVisionMode(mode: ColourVisionMode) = update { it.copy(colourVisionMode = mode) }
    suspend fun setColourTint(argb: Int, strength: Float) =
        update { it.copy(colourTint = argb, tintStrength = strength) }
    suspend fun setHighContrast(enabled: Boolean) = update { it.copy(highContrast = enabled) }
    suspend fun setReduceMotion(enabled: Boolean) = update { it.copy(reduceMotion = enabled) }
    suspend fun setLanguageTag(tag: String) = update { it.copy(languageTag = tag) }

    /** Back to system theme, default text size, no filter and system language. */
    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    companion object {
        @Volatile private var instance: UserPreferencesRepository? = null

        /** The app-wide repository, on the app's single preferences file. */
        fun get(context: Context): UserPreferencesRepository =
            instance ?: synchronized(this) {
                instance ?: UserPreferencesRepository(context.applicationContext.userPreferencesStore)
                    .also { instance = it }
            }
    }
}

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    // A corrupted file must not crash the app: start again from the defaults.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

private fun Preferences.byName(): Map<String, Any?> = asMap().mapKeys { it.key.name }

private fun MutablePreferences.write(preferences: UserPreferences) {
    encodePreferences(preferences).forEach { (name, value) ->
        when (value) {
            is String -> this[stringPreferencesKey(name)] = value
            is Boolean -> this[booleanPreferencesKey(name)] = value
            is Float -> this[floatPreferencesKey(name)] = value
            is Int -> this[intPreferencesKey(name)] = value
        }
    }
}
