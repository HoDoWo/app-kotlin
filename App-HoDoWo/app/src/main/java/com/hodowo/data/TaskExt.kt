package com.hodowo.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Waits for a Google Play Services [Task] without blocking the thread.
 *
 * A local extension (instead of `kotlinx-coroutines-play-services`) is used on
 * purpose: the unit tests drive the listeners of a mocked [Task], and the
 * library implementation listens with a different callback they do not stub.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
