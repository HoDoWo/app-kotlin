package com.hodowo.viewmodel

import kotlin.math.abs

/** The text sizes offered in Settings (#210); the fine-grained slider comes with #216. */
enum class TextSizeOption(val scale: Float) {
    SMALL(0.85f),
    DEFAULT(1f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f)
}

private const val SCALE_TOLERANCE = 0.005f

/** The option a stored scale matches, or null for a custom value (shown as a percentage). */
fun textSizeOptionFor(scale: Float): TextSizeOption? =
    TextSizeOption.entries.firstOrNull { abs(it.scale - scale) < SCALE_TOLERANCE }

/** The stored scale as a percentage, e.g. 1.2 → 120. */
fun textSizePercent(scale: Float): Int = Math.round(scale * PERCENT)

private const val PERCENT = 100f

/**
 * An app language: its BCP 47 tag ("" follows the system) and its name in itself,
 * which is how language lists are written so anyone can find their own.
 */
data class LanguageOption(val tag: String, val nativeName: String?)

val APP_LANGUAGES = listOf(
    LanguageOption("", null),
    LanguageOption("en", "English"),
    LanguageOption("it", "Italiano"),
    LanguageOption("es", "Español"),
    LanguageOption("ca", "Català")
)

/** The option for a stored tag; an unknown tag counts as the system language. */
fun languageOptionFor(tag: String): LanguageOption =
    APP_LANGUAGES.firstOrNull { it.tag == tag } ?: APP_LANGUAGES.first()
