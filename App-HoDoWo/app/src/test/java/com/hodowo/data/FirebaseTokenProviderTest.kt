package com.hodowo.data

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executor
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseTokenProviderTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var tokenProviderInstance: FirebaseTokenProvider
    private val mockUser: FirebaseUser = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        firebaseAuth = mockk(relaxed = true)
        tokenProviderInstance = FirebaseTokenProvider(firebaseAuth)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun <T> createSuccessTask(result: T): Task<T> {
        val task = mockk<Task<T>>(relaxed = true)
        val successSlot = slot<OnSuccessListener<T>>()
        every { task.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(result)
            task
        }
        val executorSlot = slot<Executor>()
        val successSlotWithExecutor = slot<OnSuccessListener<T>>()
        every { task.addOnSuccessListener(capture(executorSlot), capture(successSlotWithExecutor)) } answers {
            successSlotWithExecutor.captured.onSuccess(result)
            task
        }
        return task
    }

    private fun createFailureTask(exception: Exception): Task<GetTokenResult> {
        val task = mockk<Task<GetTokenResult>>(relaxed = true)
        val failureSlot = slot<OnFailureListener>()
        every { task.addOnFailureListener(capture(failureSlot)) } answers {
            failureSlot.captured.onFailure(exception)
            task
        }
        val executorSlot = slot<Executor>()
        val failureSlotWithExecutor = slot<OnFailureListener>()
        every { task.addOnFailureListener(capture(executorSlot), capture(failureSlotWithExecutor)) } answers {
            failureSlotWithExecutor.captured.onFailure(exception)
            task
        }
        return task
    }

    @Test
    fun getTokenReturnsTokenWhenUserIsLoggedInAndTaskSucceeds() = runTest(testDispatcher) {
        every { firebaseAuth.currentUser } returns mockUser

        val result = mockk<GetTokenResult>(relaxed = true)
        every { result.token } returns "sample-firebase-token-123"

        val task = createSuccessTask(result)
        every { mockUser.getIdToken(any()) } returns task

        val token = tokenProviderInstance.token()
        testScheduler.advanceUntilIdle()

        assertEquals("sample-firebase-token-123", token)
    }

    @Test
    fun getTokenReturnsNullWhenNoCurrentUser() = runTest(testDispatcher) {
        every { firebaseAuth.currentUser } returns null
        val authResult = mockk<AuthResult>(relaxed = true)
        every { authResult.user } returns null
        every { firebaseAuth.signInAnonymously() } returns createSuccessTask(authResult)

        val token = tokenProviderInstance.token()
        testScheduler.advanceUntilIdle()

        assertNull(token)
    }

    @Test
    fun getTokenSignsInAnonymouslyWhenNoCurrentUser() = runTest(testDispatcher) {
        every { firebaseAuth.currentUser } returns null
        val anonUser = mockk<FirebaseUser>(relaxed = true)
        val authResult = mockk<AuthResult>(relaxed = true)
        every { authResult.user } returns anonUser
        every { firebaseAuth.signInAnonymously() } returns createSuccessTask(authResult)

        val result = mockk<GetTokenResult>(relaxed = true)
        every { result.token } returns "anon-token-123"
        every { anonUser.getIdToken(any()) } returns createSuccessTask(result)

        val token = tokenProviderInstance.token()
        testScheduler.advanceUntilIdle()

        assertEquals("anon-token-123", token)
    }

    @Test
    fun getTokenThrowsOnFailure() = runTest(testDispatcher) {
        every { firebaseAuth.currentUser } returns mockUser

        val task = createFailureTask(RuntimeException("Token fetch failed"))
        every { mockUser.getIdToken(any()) } returns task

        assertFailsWith<RuntimeException> {
            tokenProviderInstance.token()
        }
        testScheduler.advanceUntilIdle()
    }
}