package com.hodowo.viewmodel

/** Pure form validation (no Android classes, so it is trivial to unit test). */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8

    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email."
        !emailRegex.matches(email.trim()) -> "Enter a valid email."
        else -> null
    }

    fun passwordError(password: String, checkLength: Boolean): String? = when {
        password.isEmpty() -> "Enter your password."
        checkLength && password.length < MIN_PASSWORD_LENGTH ->
            "Use at least $MIN_PASSWORD_LENGTH characters."
        else -> null
    }

    fun confirmError(password: String, confirm: String): String? =
        if (password != confirm) "Passwords do not match." else null
}
