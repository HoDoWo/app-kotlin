package com.hodowo.data.auth

/** Everything that can go wrong in the login / register / Google flows. */
enum class AuthError(val message: String) {
    INVALID_CREDENTIALS("Incorrect email or password."),
    USER_NOT_FOUND("There is no account with this email."),
    EMAIL_ALREADY_IN_USE("An account with this email already exists."),
    WEAK_PASSWORD("The password is too weak."),
    NETWORK("No connection. Check your internet and try again."),
    TOO_MANY_REQUESTS("Too many attempts. Try again in a few minutes."),
    GOOGLE_NOT_CONFIGURED("Google sign-in is not configured in this build."),
    GOOGLE_NO_ACCOUNT("No Google account is available on this device."),
    GOOGLE_FAILED("Google sign-in failed. Try again."),
    UNKNOWN("Something went wrong. Try again."),
}

/** Thrown by the auth layer so the ViewModel never depends on Firebase types. */
class AuthException(val error: AuthError, cause: Throwable? = null) :
    Exception(error.message, cause)

/** Email/password and Google authentication, independent of Firebase. */
interface AuthRepository {

    /** True when a real (non-anonymous) user is signed in. */
    val isSignedIn: Boolean

    /** @throws AuthException */
    suspend fun signIn(email: String, password: String)

    /** @throws AuthException */
    suspend fun register(email: String, password: String)

    /** Signs in with the ID token returned by Google. @throws AuthException */
    suspend fun signInWithGoogle(idToken: String)

    fun signOut()
}
