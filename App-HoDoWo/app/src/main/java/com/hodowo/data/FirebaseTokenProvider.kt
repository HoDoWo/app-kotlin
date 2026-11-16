package com.hodowo.data

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException

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
 *
 * Without connection Firebase fails with a [FirebaseException]: it is turned
 * into an [IOException], the error every service already handles ("check your
 * connection"), so it never crashes the app.
 */
class FirebaseTokenProvider(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun token(): String? = try {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        user?.getIdToken(false)?.await()?.token
    } catch (e: FirebaseException) {
        throw IOException("No connection to the server, check your connection", e)
    }
}
