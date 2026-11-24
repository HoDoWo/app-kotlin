package com.hodowo.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AR availability (#239): supported, unsupported and transient answers of ARCore. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArAvailabilityRepositoryTest {

    /** Answers in order; the last answer repeats. Counts the calls. */
    private class FakeChecker(vararg answers: ArCheck) : ArAvailabilityChecker {
        private val queue = answers.toMutableList()
        var calls = 0
            private set

        override fun check(): ArCheck {
            calls++
            return if (queue.size > 1) queue.removeAt(0) else queue.first()
        }
    }

    @Test
    fun startsAsCheckingSoNoEntryPointIsShownBeforeTheAnswer() {
        val repository = ArAvailabilityRepository(FakeChecker(ArCheck.INSTALLED))
        assertEquals(ArSupport.CHECKING, repository.state.value)
        assertFalse(repository.state.value.offersAr)
    }

    @Test
    fun installedIsReady() = runTest {
        val repository = ArAvailabilityRepository(FakeChecker(ArCheck.INSTALLED))
        assertEquals(ArSupport.READY, repository.refresh())
        assertEquals(ArSupport.READY, repository.state.value)
        assertTrue(repository.state.value.offersAr)
    }

    @Test
    fun notCapableIsUnsupported() = runTest {
        val repository = ArAvailabilityRepository(FakeChecker(ArCheck.UNSUPPORTED))
        assertEquals(ArSupport.UNSUPPORTED, repository.refresh())
        assertFalse(repository.state.value.offersAr)
    }

    @Test
    fun missingOrOldServicesNeedInstallButStillOfferAr() = runTest {
        assertEquals(ArSupport.NEEDS_INSTALL, ArAvailabilityRepository(FakeChecker(ArCheck.NOT_INSTALLED)).refresh())
        assertEquals(ArSupport.NEEDS_INSTALL, ArAvailabilityRepository(FakeChecker(ArCheck.APK_TOO_OLD)).refresh())
        assertTrue(ArSupport.NEEDS_INSTALL.offersAr)
    }

    @Test
    fun checkingIsAskedAgainUntilARealAnswer() = runTest {
        val checker = FakeChecker(ArCheck.CHECKING, ArCheck.CHECKING, ArCheck.INSTALLED)
        val repository = ArAvailabilityRepository(checker, pollDelayMs = 200, maxPolls = 25)

        assertEquals(ArSupport.READY, repository.refresh())
        assertEquals(3, checker.calls)
    }

    @Test
    fun stateStaysCheckingWhileARCoreIsStillChecking() = runTest {
        val checker = FakeChecker(ArCheck.CHECKING, ArCheck.UNSUPPORTED)
        val repository = ArAvailabilityRepository(checker, pollDelayMs = 200, maxPolls = 25)

        launch { repository.refresh() }
        runCurrent()
        assertEquals(ArSupport.CHECKING, repository.state.value)

        advanceTimeBy(201)
        assertEquals(ArSupport.UNSUPPORTED, repository.state.value)
    }

    @Test
    fun neverEndingCheckGivesUnknownAfterTheLastPoll() = runTest {
        val checker = FakeChecker(ArCheck.CHECKING)
        val repository = ArAvailabilityRepository(checker, pollDelayMs = 200, maxPolls = 5)

        assertEquals(ArSupport.UNKNOWN, repository.refresh())
        assertEquals(5, checker.calls)
        assertFalse(repository.state.value.offersAr)
    }

    @Test
    fun timeoutOrErrorIsUnknown() = runTest {
        assertEquals(ArSupport.UNKNOWN, ArAvailabilityRepository(FakeChecker(ArCheck.UNKNOWN)).refresh())
    }

    @Test
    fun anExceptionFromARCoreIsUnknownNotACrash() = runTest {
        val repository = ArAvailabilityRepository(ArAvailabilityChecker { throw IllegalStateException("no ARCore") })
        assertEquals(ArSupport.UNKNOWN, repository.refresh())
    }

    @Test
    fun aFinalAnswerIsCachedAndNotAskedAgain() = runTest {
        val ready = FakeChecker(ArCheck.INSTALLED)
        val readyRepository = ArAvailabilityRepository(ready)
        readyRepository.refresh()
        readyRepository.refresh()
        assertEquals(1, ready.calls)

        val unsupported = FakeChecker(ArCheck.UNSUPPORTED)
        val unsupportedRepository = ArAvailabilityRepository(unsupported)
        unsupportedRepository.refresh()
        unsupportedRepository.refresh()
        assertEquals(1, unsupported.calls)
    }

    @Test
    fun unknownIsCheckedAgainOnRetry() = runTest {
        val checker = FakeChecker(ArCheck.UNKNOWN, ArCheck.INSTALLED)
        val repository = ArAvailabilityRepository(checker)

        assertEquals(ArSupport.UNKNOWN, repository.refresh())
        assertEquals(ArSupport.READY, repository.refresh())
        assertEquals(2, checker.calls)
    }

    @Test
    fun needsInstallIsCheckedAgainSoAnInstallIsSeen() = runTest {
        val checker = FakeChecker(ArCheck.NOT_INSTALLED, ArCheck.INSTALLED)
        val repository = ArAvailabilityRepository(checker)

        assertEquals(ArSupport.NEEDS_INSTALL, repository.refresh())
        assertEquals(ArSupport.READY, repository.refresh())
    }

    @Test
    fun everyARCoreAnswerHasASupportState() {
        val expected = mapOf(
            ArCheck.CHECKING to ArSupport.CHECKING,
            ArCheck.UNKNOWN to ArSupport.UNKNOWN,
            ArCheck.UNSUPPORTED to ArSupport.UNSUPPORTED,
            ArCheck.NOT_INSTALLED to ArSupport.NEEDS_INSTALL,
            ArCheck.APK_TOO_OLD to ArSupport.NEEDS_INSTALL,
            ArCheck.INSTALLED to ArSupport.READY
        )
        ArCheck.values().forEach { assertEquals(expected.getValue(it), it.toSupport()) }
    }
}
