package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.auth.AuthError
import com.hodowo.data.auth.AuthException
import com.hodowo.data.auth.AuthRepository
import com.hodowo.data.auth.GoogleSignInCancelledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, REGISTER }

/** Everything the login / register screens need to render themselves. */
data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    /** Error that is not tied to a single field (wrong password, network...). */
    val formError: String? = null,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isSignedIn = repository.isSignedIn))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null, formError = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }

    /** Switches between login and register, keeping the email but clearing errors. */
    fun setMode(mode: AuthMode) = _uiState.update {
        AuthUiState(mode = mode, email = it.email, isSignedIn = it.isSignedIn)
    }

    /** Validates the form and signs in or registers, depending on the mode. */
    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        val isRegister = state.mode == AuthMode.REGISTER
        val emailError = AuthValidator.emailError(state.email)
        val passwordError = AuthValidator.passwordError(state.password, checkLength = isRegister)
        val confirmError =
            if (isRegister) AuthValidator.confirmError(state.password, state.confirmPassword) else null

        if (emailError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(emailError = emailError, passwordError = passwordError, confirmError = confirmError)
            }
            return
        }

        val email = state.email.trim()
        runAuth {
            if (isRegister) repository.register(email, state.password)
            else repository.signIn(email, state.password)
        }
    }

    /**
     * Signs in with Google. [getIdToken] shows the account picker (it needs an
     * Activity context, so the screen provides it) and returns the ID token.
     */
    fun signInWithGoogle(getIdToken: suspend () -> String) {
        if (_uiState.value.isLoading) return
        runAuth { repository.signInWithGoogle(getIdToken()) }
    }

    fun signOut() {
        repository.signOut()
        _uiState.value = AuthUiState()
    }

    private fun runAuth(block: suspend () -> Unit) {
        _uiState.update { it.copy(isLoading = true, formError = null) }
        viewModelScope.launch {
            try {
                block()
                _uiState.update { it.copy(isLoading = false, isSignedIn = true, password = "", confirmPassword = "") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: GoogleSignInCancelledException) {
                // The user closed the account picker: nothing to show.
                _uiState.update { it.copy(isLoading = false) }
            } catch (error: AuthException) {
                _uiState.update { it.copy(isLoading = false, formError = error.error.message) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, formError = AuthError.UNKNOWN.message) }
            }
        }
    }
}
