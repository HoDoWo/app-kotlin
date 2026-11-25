package com.hodowo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/**
 * The user's locale, read so that Compose recomposes when it changes
 * (lint NonObservableLocale): use it instead of Locale.getDefault() in composables.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
