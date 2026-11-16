package com.hodowo.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the pure form validation of the login / register screen (#54).
 */
class AuthValidatorTest {

    // ---------- emailError ----------

    @Test
    fun blankEmailIsRejected() {
        assertEquals("Enter your email.", AuthValidator.emailError(""))
        assertEquals("Enter your email.", AuthValidator.emailError("   "))
    }

    @Test
    fun emailWithoutAtIsRejected() {
        assertEquals("Enter a valid email.", AuthValidator.emailError("user.example.com"))
    }

    @Test
    fun emailWithoutDomainIsRejected() {
        assertEquals("Enter a valid email.", AuthValidator.emailError("user@example"))
    }

    @Test
    fun emailWithSpacesIsRejected() {
        assertEquals("Enter a valid email.", AuthValidator.emailError("user name@example.com"))
    }

    @Test
    fun validEmailIsAccepted() {
        assertNull(AuthValidator.emailError("user@example.com"))
        assertNull(AuthValidator.emailError("first.last@example.co"))
        assertNull(AuthValidator.emailError("user+tag@example.com"))
    }

    @Test
    fun emailIsTrimmedBeforeBeingValidated() {
        assertNull(AuthValidator.emailError("  user@example.com  "))
    }

    // ---------- passwordError ----------

    @Test
    fun emptyPasswordIsRejected() {
        assertEquals("Enter your password.", AuthValidator.passwordError("", checkLength = true))
        assertEquals("Enter your password.", AuthValidator.passwordError("", checkLength = false))
    }

    @Test
    fun shortPasswordIsRejectedWhenTheLengthMatters() {
        assertEquals(
            "Use at least ${AuthValidator.MIN_PASSWORD_LENGTH} characters.",
            AuthValidator.passwordError("short", checkLength = true)
        )
    }

    @Test
    fun shortPasswordIsAcceptedWhenLoggingIn() {
        // The length is only enforced when the account is created.
        assertNull(AuthValidator.passwordError("short", checkLength = false))
    }

    @Test
    fun longEnoughPasswordIsAccepted() {
        assertNull(AuthValidator.passwordError("12345678", checkLength = true))
    }

    // ---------- confirmError ----------

    @Test
    fun differentPasswordsAreRejected() {
        assertEquals(
            "Passwords do not match.",
            AuthValidator.confirmError("12345678", "87654321")
        )
    }

    @Test
    fun samePasswordsAreAccepted() {
        assertNull(AuthValidator.confirmError("12345678", "12345678"))
    }
}
