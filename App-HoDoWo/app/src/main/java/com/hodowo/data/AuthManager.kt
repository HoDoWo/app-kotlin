package com.hodowo.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    suspend fun signIn(email: String, pass: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
        val user = result.user ?: throw IllegalStateException("Sign in failed")
        _currentUser.value = user
        return user
    }

    suspend fun signUp(email: String, pass: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
        val user = result.user ?: throw IllegalStateException("Sign up failed")
        _currentUser.value = user
        return user
    }

    suspend fun signInAnonymously(): FirebaseUser {
        val current = auth.currentUser
        if (current != null) return current
        val result = auth.signInAnonymously().await()
        val user = result.user ?: throw IllegalStateException("Anonymous sign in failed")
        _currentUser.value = user
        return user
    }

    fun signOut() {
        auth.signOut()
        _currentUser.value = null
    }

    val currentUserId: String
        get() = auth.currentUser?.uid ?: "anonymous"

    val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous ?: true
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
