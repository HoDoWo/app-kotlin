package com.hodowo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hodowo.data.AppLanguage
import com.hodowo.data.ColourVisionMode
import com.hodowo.data.ThemeMode
import com.hodowo.data.UserPreferences
import com.hodowo.data.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The appearance, accessibility and language choices for the whole app (#211).
 * [preferences] is null until the store has been read once, so the first frame can
 * wait for it instead of flashing the default theme.
 */
class AppPreferencesViewModel(
    private val repository: UserPreferencesRepository,
    /** Shows the app in a language (#235); a no-op in tests. */
    private val applyLanguage: (String) -> Unit = {}
) : ViewModel() {

    val preferences: StateFlow<UserPreferences?> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) = launch { repository.setThemeMode(mode) }
    fun setDynamicColour(enabled: Boolean) = launch { repository.setDynamicColour(enabled) }
    fun setFontScale(scale: Float) = launch { repository.setFontScale(scale) }
    fun setColourVisionMode(mode: ColourVisionMode) = launch { repository.setColourVisionMode(mode) }
    fun setColourTint(argb: Int, strength: Float) = launch { repository.setColourTint(argb, strength) }
    fun setHighContrast(enabled: Boolean) = launch { repository.setHighContrast(enabled) }
    fun setReduceMotion(enabled: Boolean) = launch { repository.setReduceMotion(enabled) }

    /** Stores the language, then shows the app in it (this recreates the activity once). */
    fun setLanguageTag(tag: String) = launch {
        repository.setLanguageTag(tag)
        applyLanguage(tag)
    }

    fun reset() = launch {
        repository.reset()
        applyLanguage("")
    }

    /**
     * At start: the language the app really shows ([shown]) wins over the stored one,
     * since it can also be changed in the system's per-app language settings.
     * Only the store is updated, so this never changes the language or loops.
     */
    fun syncLanguage(shown: String) = launch {
        repository.update { stored -> if (stored.languageTag == shown) stored else stored.copy(languageTag = shown) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppPreferencesViewModel(UserPreferencesRepository.get(context), AppLanguage::apply) }
        }
    }
}
