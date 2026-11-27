package com.hodowo.viewmodel

/** Pure form validation (no Android classes, so it is trivial to unit test). */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8

    // English texts; the UI shows them in the app language (AuthMessages.kt, #230).
    const val EMAIL_EMPTY = "Enter your email."
    const val EMAIL_INVALID = "Enter a valid email."
    const val PASSWORD_EMPTY = "Enter your password."
    const val PASSWORD_SHORT = "Use at least $MIN_PASSWORD_LENGTH characters."
    const val PASSWORDS_DIFFER = "Passwords do not match."

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> EMAIL_EMPTY
        !emailRegex.matches(email.trim()) -> EMAIL_INVALID
        else -> null
    }

    fun passwordError(password: String, checkLength: Boolean): String? = when {
        password.isEmpty() -> PASSWORD_EMPTY
        checkLength && password.length < MIN_PASSWORD_LENGTH -> PASSWORD_SHORT
        else -> null
    }

    fun confirmError(password: String, confirm: String): String? =
        if (password != confirm) PASSWORDS_DIFFER else null
}
