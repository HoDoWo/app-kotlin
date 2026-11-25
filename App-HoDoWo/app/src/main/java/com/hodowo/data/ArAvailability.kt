package com.hodowo.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What ARCore answers when asked whether this phone can do AR (#239). It mirrors
 * `ArCoreApk.Availability`, so the logic below stays plain Kotlin and can be
 * tested without a phone.
 */
enum class ArCheck {
    /** ARCore is still finding out (it may need the network): ask again shortly. */
    CHECKING,

    /** The check timed out or failed: the answer is unknown for now. */
    UNKNOWN,

    /** The phone is not ARCore certified. */
    UNSUPPORTED,

    /** The phone is supported, but Google Play Services for AR is not installed. */
    NOT_INSTALLED,

    /** The phone is supported, but Google Play Services for AR must be updated. */
    APK_TOO_OLD,

    /** The phone is supported and ready. */
    INSTALLED
}

/** What the app shows for AR. */
enum class ArSupport {
    /** Still checking: show no AR entry point yet. */
    CHECKING,

    /** AR can start now. */
    READY,

    /** The phone supports AR; Google Play Services for AR has to be installed or updated first. */
    NEEDS_INSTALL,

    /** The phone cannot do AR: explain it and offer the 2D guide. */
    UNSUPPORTED,

    /** The check did not finish (offline, timeout, error): offer a retry and the 2D guide. */
    UNKNOWN;

    /** The phone can do AR, possibly after installing Google Play Services for AR. */
    val offersAr: Boolean get() = this == READY || this == NEEDS_INSTALL
}

fun ArCheck.toSupport(): ArSupport = when (this) {
    ArCheck.CHECKING -> ArSupport.CHECKING
    ArCheck.UNKNOWN -> ArSupport.UNKNOWN
    ArCheck.UNSUPPORTED -> ArSupport.UNSUPPORTED
    ArCheck.NOT_INSTALLED, ArCheck.APK_TOO_OLD -> ArSupport.NEEDS_INSTALL
    ArCheck.INSTALLED -> ArSupport.READY
}

/** Asks ARCore once; the Android implementation is [ArCoreAvailabilityChecker]. */
fun interface ArAvailabilityChecker {
    fun check(): ArCheck
}

/**
 * The AR support of this phone, checked once and kept for the app's lifetime.
 *
 * ARCore often answers "still checking" on the first call, so [refresh] asks
 * again every [pollDelayMs] up to [maxPolls] times. A final answer (ready or
 * unsupported) is cached: the phone does not change. "Needs install" and
 * "unknown" are not cached, so the next [refresh] checks again, for example
 * after the user installed Google Play Services for AR or came back online.
 */
class ArAvailabilityRepository(
    private val checker: ArAvailabilityChecker,
    private val pollDelayMs: Long = POLL_DELAY_MS,
    private val maxPolls: Int = MAX_POLLS
) {
    private val _state = MutableStateFlow(ArSupport.CHECKING)
    val state: StateFlow<ArSupport> = _state.asStateFlow()

    private val mutex = Mutex()

    suspend fun refresh(): ArSupport = mutex.withLock {
        val cached = _state.value
        if (cached == ArSupport.READY || cached == ArSupport.UNSUPPORTED) return cached
        // A retry after "unknown" shows the spinner; "needs install" stays on
        // screen while it is checked again, so the tab does not flicker.
        if (cached == ArSupport.UNKNOWN) _state.value = ArSupport.CHECKING
        val result = poll()
        _state.value = result
        result
    }

    private suspend fun poll(): ArSupport {
        repeat(maxPolls) { attempt ->
            val check = try {
                checker.check()
            } catch (expected: RuntimeException) {
                // ARCore throws on some unsupported setups instead of answering:
                // treat it as unknown, so the user can retry and keeps the 2D guide.
                ArCheck.UNKNOWN
            }
            if (check != ArCheck.CHECKING) return check.toSupport()
            if (attempt < maxPolls - 1) delay(pollDelayMs)
        }
        return ArSupport.UNKNOWN
    }

    companion object {
        /** Google suggests asking again after about 200 ms while ARCore is checking. */
        const val POLL_DELAY_MS = 200L

        /** About 5 seconds in all before giving up as unknown. */
        const val MAX_POLLS = 25
    }
}
