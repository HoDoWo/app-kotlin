package com.hodowo.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hodowo.R
import com.hodowo.data.ThemeMode
import com.hodowo.data.UserPreferences
import com.hodowo.viewmodel.APP_LANGUAGES
import com.hodowo.viewmodel.AppPreferencesViewModel
import com.hodowo.viewmodel.LanguageOption
import com.hodowo.viewmodel.TextSizeOption
import com.hodowo.viewmodel.languageOptionFor
import com.hodowo.viewmodel.textSizeOptionFor
import com.hodowo.viewmodel.textSizePercent

private val ROW_MIN_HEIGHT = 56.dp
private val SCREEN_PADDING = 16.dp

/** Which choice dialog is open. */
private enum class SettingsDialog { THEME, TEXT_SIZE, LANGUAGE, RESET }

/**
 * Settings hub (#210): appearance, accessibility and language in one place, each row
 * showing its current value. Every change is stored at once (#211) and applied to the
 * whole app without a restart (#212).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppPreferencesViewModel, onBack: () -> Unit) {
    val preferences by viewModel.preferences.collectAsState()
    val current = preferences ?: UserPreferences()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(R.string.settings_section_appearance) }
            item {
                ValueRow(
                    title = stringResource(R.string.settings_theme),
                    value = stringResource(current.themeMode.label()),
                    onClick = { dialog = SettingsDialog.THEME }
                )
            }
            // Wallpaper colours exist from Android 12.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_colour),
                        summary = stringResource(R.string.settings_dynamic_colour_summary),
                        checked = current.dynamicColour,
                        onChange = viewModel::setDynamicColour
                    )
                }
            }

            item { SectionHeader(R.string.settings_section_accessibility) }
            item {
                ValueRow(
                    title = stringResource(R.string.settings_text_size),
                    value = textSizeLabel(current.fontScale),
                    onClick = { dialog = SettingsDialog.TEXT_SIZE }
                )
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.settings_high_contrast),
                    summary = stringResource(R.string.settings_high_contrast_summary),
                    checked = current.highContrast,
                    onChange = viewModel::setHighContrast
                )
            }

            item { SectionHeader(R.string.settings_section_language) }
            item {
                ValueRow(
                    title = stringResource(R.string.settings_app_language),
                    value = languageLabel(languageOptionFor(current.languageTag)),
                    onClick = { dialog = SettingsDialog.LANGUAGE }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(top = SCREEN_PADDING))
                OutlinedButton(
                    onClick = { dialog = SettingsDialog.RESET },
                    modifier = Modifier.fillMaxWidth().padding(SCREEN_PADDING)
                ) {
                    Text(stringResource(R.string.settings_reset))
                }
            }
        }
    }

    val close = { dialog = null }
    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(it.label()) },
            selected = current.themeMode,
            onSelect = { viewModel.setThemeMode(it); close() },
            onDismiss = close
        )
        SettingsDialog.TEXT_SIZE -> ChoiceDialog(
            title = stringResource(R.string.settings_text_size),
            options = TextSizeOption.entries.map { it to stringResource(it.label()) },
            selected = textSizeOptionFor(current.fontScale),
            onSelect = { viewModel.setFontScale(it.scale); close() },
            onDismiss = close,
            footer = {
                Text(
                    text = stringResource(R.string.settings_text_size_preview),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        )
        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_app_language),
            options = APP_LANGUAGES.map { it to languageLabel(it) },
            selected = languageOptionFor(current.languageTag),
            onSelect = { viewModel.setLanguageTag(it.tag); close() },
            onDismiss = close
        )
        SettingsDialog.RESET -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); close() }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = close) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SCREEN_PADDING, end = SCREEN_PADDING, top = 24.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/** A row that opens a choice; TalkBack reads its title, its value and that it is a button. */
@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = value }
            .padding(horizontal = SCREEN_PADDING, vertical = 8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** An on/off row: the whole row is the switch, so it is easy to hit. */
@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val state = stringResource(if (checked) R.string.settings_on else R.string.settings_off)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { stateDescription = state }
            .padding(horizontal = SCREEN_PADDING, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(SCREEN_PADDING))
        // The row handles the click; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A single-choice dialog with radio buttons; choosing closes it. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (option, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                footer()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        }
    )
}

@StringRes
private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@StringRes
private fun TextSizeOption.label(): Int = when (this) {
    TextSizeOption.SMALL -> R.string.settings_text_size_small
    TextSizeOption.DEFAULT -> R.string.settings_text_size_default
    TextSizeOption.LARGE -> R.string.settings_text_size_large
    TextSizeOption.EXTRA_LARGE -> R.string.settings_text_size_extra_large
}

@Composable
private fun textSizeLabel(scale: Float): String =
    textSizeOptionFor(scale)?.let { stringResource(it.label()) }
        ?: stringResource(R.string.settings_text_size_custom, textSizePercent(scale))

@Composable
private fun languageLabel(option: LanguageOption): String =
    option.nativeName ?: stringResource(R.string.settings_language_system)
