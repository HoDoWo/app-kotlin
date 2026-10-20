package com.hodowo.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.hodowo.data.await
import kotlinx.coroutines.CancellationException

/** [AuthRepository] backed by Firebase Authentication. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : AuthRepository {

    // Anonymous users (FirebaseTokenProvider fallback) must still see the login.
    override val isSignedIn: Boolean
        get() = auth.currentUser?.isAnonymous == false

    override suspend fun signIn(email: String, password: String) {
        wrap { auth.signInWithEmailAndPassword(email, password).await() }
    }

    override suspend fun register(email: String, password: String) {
        wrap { auth.createUserWithEmailAndPassword(email, password).await() }
    }

    override suspend fun signInWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        wrap { auth.signInWithCredential(credential).await() }
    }

    override fun signOut() = auth.signOut()

    private suspend fun wrap(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw AuthException(error.toAuthError(), error)
        }
    }

    private fun Exception.toAuthError(): AuthError = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_ALREADY_IN_USE
        is FirebaseAuthInvalidUserException -> AuthError.USER_NOT_FOUND
        is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIALS
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        is FirebaseNetworkException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }
}
