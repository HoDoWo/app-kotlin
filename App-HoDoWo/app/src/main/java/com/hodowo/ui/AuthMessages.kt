package com.hodowo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hodowo.R
import com.hodowo.data.auth.AuthError
import com.hodowo.viewmodel.AuthValidator

/**
 * An auth form message in the app language (#230). The validator and the auth layer
 * speak English, so their tests and logs stay readable; the UI translates the texts
 * it knows and shows anything else (e.g. a server message) as it is.
 */
@Composable
fun authMessage(message: String): String = when (message) {
    AuthValidator.EMAIL_EMPTY -> stringResource(R.string.auth_error_email_empty)
    AuthValidator.EMAIL_INVALID -> stringResource(R.string.auth_error_email_invalid)
    AuthValidator.PASSWORD_EMPTY -> stringResource(R.string.auth_error_password_empty)
    AuthValidator.PASSWORD_SHORT ->
        stringResource(R.string.auth_error_password_short, AuthValidator.MIN_PASSWORD_LENGTH)
    AuthValidator.PASSWORDS_DIFFER -> stringResource(R.string.auth_error_passwords_differ)
    else -> AuthError.entries.firstOrNull { it.message == message }?.let { stringResource(it.res()) } ?: message
}

private fun AuthError.res(): Int = when (this) {
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.USER_NOT_FOUND -> R.string.auth_error_user_not_found
    AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_error_email_already_in_use
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.TOO_MANY_REQUESTS -> R.string.auth_error_too_many_requests
    AuthError.GOOGLE_NOT_CONFIGURED -> R.string.auth_error_google_not_configured
    AuthError.GOOGLE_NO_ACCOUNT -> R.string.auth_error_google_no_account
    AuthError.GOOGLE_FAILED -> R.string.auth_error_google_failed
    AuthError.GOOGLE_PROVIDER_DISABLED -> R.string.auth_error_google_provider_disabled
    AuthError.GOOGLE_ACCOUNT_CONFLICT -> R.string.auth_error_google_account_conflict
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
