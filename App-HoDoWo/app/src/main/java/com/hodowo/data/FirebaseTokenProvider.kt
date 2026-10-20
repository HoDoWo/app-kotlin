package com.hodowo.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Provides the Firebase ID token that the backend expects in
 * "Authorization: Bearer <token>".
 *
 * - If a user is signed in (login screen, #54), their token is used.
 * - Otherwise the app signs in anonymously, so the chat works before the
 *   login screen exists. "Anonymous" must be enabled in
 *   Firebase console -> Authentication -> Sign-in method.
 *
 * Firebase caches the token and refreshes it automatically when it expires.
 */
class FirebaseTokenProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun token(): String? {
        val user = auth.currentUser
            ?: auth.signInAnonymously().await().user
            ?: return null

        return user.getIdToken(false).await().token
    }
}
