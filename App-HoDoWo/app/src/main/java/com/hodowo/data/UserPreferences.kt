package com.hodowo.data

/** Light, dark or whatever the system uses (#168). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The colour-vision filter applied over the whole app (#219-#224). */
enum class ColourVisionMode { NONE, PROTANOPIA, DEUTERANOPIA, TRITANOPIA, GRAYSCALE, TINT }

/**
 * Appearance, accessibility and language choices (#211). The defaults are what the
 * app does without any choice: system theme, 100 % text, no filter, system language.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColour: Boolean = false,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val colourVisionMode: ColourVisionMode = ColourVisionMode.NONE,
    /** ARGB colour of the tint mode. */
    val colourTint: Int = DEFAULT_TINT,
    /** 0 (no tint) .. 1 (full tint). */
    val tintStrength: Float = DEFAULT_TINT_STRENGTH,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    /** BCP 47 tag such as "it" or "es"; empty means the system language. */
    val languageTag: String = ""
) {
    companion object {
        const val DEFAULT_FONT_SCALE = 1f
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 2f
        const val DEFAULT_TINT = 0xFFFFC107.toInt()
        const val DEFAULT_TINT_STRENGTH = 0.3f
    }
}

/** The names the preferences are stored under; they must never change once shipped. */
object PreferenceKeys {
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOUR = "dynamic_colour"
    const val FONT_SCALE = "font_scale"
    const val COLOUR_VISION_MODE = "colour_vision_mode"
    const val COLOUR_TINT = "colour_tint"
    const val TINT_STRENGTH = "tint_strength"
    const val HIGH_CONTRAST = "high_contrast"
    const val REDUCE_MOTION = "reduce_motion"
    const val LANGUAGE_TAG = "language_tag"
}

private val LANGUAGE_TAG_PATTERN = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$")

/**
 * Reads stored values ([stored]: preference name to value) into [UserPreferences].
 * Anything missing, of the wrong type or out of range falls back to its default,
 * so a corrupted or older store never breaks the app.
 */
fun decodePreferences(stored: Map<String, Any?>): UserPreferences {
    val defaults = UserPreferences()
    return UserPreferences(
        themeMode = enumOrDefault(stored[PreferenceKeys.THEME_MODE], defaults.themeMode),
        dynamicColour = stored[PreferenceKeys.DYNAMIC_COLOUR] as? Boolean ?: defaults.dynamicColour,
        fontScale = (stored[PreferenceKeys.FONT_SCALE] as? Float)
            ?.takeIf { it.isFinite() }
            ?.coerceIn(UserPreferences.MIN_FONT_SCALE, UserPreferences.MAX_FONT_SCALE)
            ?: defaults.fontScale,
        colourVisionMode = enumOrDefault(stored[PreferenceKeys.COLOUR_VISION_MODE], defaults.colourVisionMode),
        colourTint = stored[PreferenceKeys.COLOUR_TINT] as? Int ?: defaults.colourTint,
        tintStrength = (stored[PreferenceKeys.TINT_STRENGTH] as? Float)
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: defaults.tintStrength,
        highContrast = stored[PreferenceKeys.HIGH_CONTRAST] as? Boolean ?: defaults.highContrast,
        reduceMotion = stored[PreferenceKeys.REDUCE_MOTION] as? Boolean ?: defaults.reduceMotion,
        languageTag = (stored[PreferenceKeys.LANGUAGE_TAG] as? String)
            ?.takeIf { it.isEmpty() || LANGUAGE_TAG_PATTERN.matches(it) }
            ?: defaults.languageTag
    )
}

/** The values to store for [preferences]; [decodePreferences] reads them back unchanged. */
fun encodePreferences(preferences: UserPreferences): Map<String, Any> = mapOf(
    PreferenceKeys.THEME_MODE to preferences.themeMode.name,
    PreferenceKeys.DYNAMIC_COLOUR to preferences.dynamicColour,
    PreferenceKeys.FONT_SCALE to preferences.fontScale,
    PreferenceKeys.COLOUR_VISION_MODE to preferences.colourVisionMode.name,
    PreferenceKeys.COLOUR_TINT to preferences.colourTint,
    PreferenceKeys.TINT_STRENGTH to preferences.tintStrength,
    PreferenceKeys.HIGH_CONTRAST to preferences.highContrast,
    PreferenceKeys.REDUCE_MOTION to preferences.reduceMotion,
    PreferenceKeys.LANGUAGE_TAG to preferences.languageTag
)

private inline fun <reified T : Enum<T>> enumOrDefault(value: Any?, default: T): T =
    (value as? String)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

/** Whether the app is dark: the user's choice, or the system's when they follow it. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Above this the layouts cannot hold the text any more (system 2× by app 2× would be 4×). */
const val MAX_APP_FONT_SCALE = 3f

/** The text scale the app uses: the system's (accessibility settings) times the user's in-app choice. */
fun appFontScale(systemScale: Float, chosenScale: Float): Float =
    (systemScale * chosenScale).coerceAtMost(MAX_APP_FONT_SCALE)
