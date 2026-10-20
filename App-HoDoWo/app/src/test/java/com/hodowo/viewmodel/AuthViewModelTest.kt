package com.hodowo.viewmodel

import com.hodowo.data.auth.AuthError
import com.hodowo.data.auth.AuthException
import com.hodowo.data.auth.AuthRepository
import com.hodowo.data.auth.GoogleSignInCancelledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AuthViewModel] (#54). The repository is faked, so no
 * Firebase connection is needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        // viewModelScope uses Dispatchers.Main, which does not exist in unit tests.
        Dispatchers.setMain(dispatcher)
        repository = FakeAuthRepository()
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------- submit ----------

    @Test
    fun invalidEmailBlocksTheSignIn() = runTest(dispatcher) {
        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("12345678")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.emailError)
        assertTrue(repository.signInCalls.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isSignedIn)
    }

    @Test
    fun shortPasswordBlocksTheRegister() = runTest(dispatcher) {
        viewModel.setMode(AuthMode.REGISTER)
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("short")
        viewModel.onConfirmPasswordChange("short")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.passwordError)
        assertTrue(repository.registerCalls.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun differentPasswordsBlockTheRegister() = runTest(dispatcher) {
        viewModel.setMode(AuthMode.REGISTER)
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.onConfirmPasswordChange("87654321")

        viewModel.submit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.confirmError)
        assertTrue(repository.registerCalls.isEmpty())
    }

    @Test
    fun validCredentialsSignIn() = runTest(dispatcher) {
        viewModel.onEmailChange("  user@example.com  ")
        viewModel.onPasswordChange("12345678")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("user@example.com" to "12345678"), repository.signInCalls)
        assertTrue(state.isSignedIn)
        assertFalse(state.isLoading)
        assertNull(state.formError)
        // The password is not kept in the state once it is used.
        assertEquals("", state.password)
    }

    @Test
    fun validCredentialsRegister() = runTest(dispatcher) {
        viewModel.setMode(AuthMode.REGISTER)
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.onConfirmPasswordChange("12345678")

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("user@example.com" to "12345678"), repository.registerCalls)
        assertTrue(state.isSignedIn)
        assertEquals("", state.confirmPassword)
    }

    // ---------- errors ----------

    @Test
    fun authExceptionBecomesTheFormError() = runTest(dispatcher) {
        repository.error = AuthException(AuthError.INVALID_CREDENTIALS)

        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthError.INVALID_CREDENTIALS.message, state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedIn)
    }

    @Test
    fun unknownErrorBecomesTheGenericMessage() = runTest(dispatcher) {
        repository.error = IllegalStateException("boom")

        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AuthError.UNKNOWN.message, state.formError)
        assertFalse(state.isLoading)
    }

    // ---------- Google ----------

    @Test
    fun googleSignInSignsTheUserIn() = runTest(dispatcher) {
        viewModel.signInWithGoogle { "id-token" }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("id-token"), repository.googleCalls)
        assertTrue(state.isSignedIn)
        assertFalse(state.isLoading)
    }

    @Test
    fun dismissedGooglePickerShowsNoError() = runTest(dispatcher) {
        repository.error = GoogleSignInCancelledException()

        viewModel.signInWithGoogle { "id-token" }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.formError)
        assertFalse(state.isLoading)
        assertFalse(state.isSignedIn)
    }

    // ---------- state ----------

    @Test
    fun switchingTheModeKeepsTheEmailAndClearsTheErrors() = runTest(dispatcher) {
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.submit()
        advanceUntilIdle()

        viewModel.setMode(AuthMode.REGISTER)

        val state = viewModel.uiState.value
        assertEquals(AuthMode.REGISTER, state.mode)
        assertEquals("user@example.com", state.email)
        assertNull(state.formError)
    }

    @Test
    fun signingOutClearsTheState() = runTest(dispatcher) {
        viewModel.onEmailChange("user@example.com")
        viewModel.onPasswordChange("12345678")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSignedIn)

        viewModel.signOut()

        val state = viewModel.uiState.value
        assertFalse(state.isSignedIn)
        assertEquals("", state.email)
        assertEquals("", state.password)
    }

    @Test
    fun anAlreadySignedInUserStartsOnTheApp() = runTest(dispatcher) {
        val signedIn = AuthViewModel(FakeAuthRepository(isSignedIn = true))

        assertTrue(signedIn.uiState.value.isSignedIn)
    }

    /** Fake repository: every call is recorded and the result is set by the test. */
    private class FakeAuthRepository(
        override val isSignedIn: Boolean = false
    ) : AuthRepository {

        val signInCalls = mutableListOf<Pair<String, String>>()
        val registerCalls = mutableListOf<Pair<String, String>>()
        val googleCalls = mutableListOf<String>()

        /** When set, every call fails with this error. */
        var error: Exception? = null

        override suspend fun signIn(email: String, password: String) {
            error?.let { throw it }
            signInCalls += email to password
        }

        override suspend fun register(email: String, password: String) {
            error?.let { throw it }
            registerCalls += email to password
        }

        override suspend fun signInWithGoogle(idToken: String) {
            error?.let { throw it }
            googleCalls += idToken
        }

        override fun signOut() = Unit
    }
}
