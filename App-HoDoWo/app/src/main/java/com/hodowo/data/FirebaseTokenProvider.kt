package com.hodowo.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Provides the Firebase ID token that the backend expects in
 * "Authorization: Bearer <token>".
 *
 * - If a user is signed in (login screen, #51), their token is used.
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

/**
 * Waits for a Google Play Services [Task] without blocking the thread
 * (avoids adding the kotlinx-coroutines-play-services dependency).
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}