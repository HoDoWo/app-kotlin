package com.hodowo.data

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.hodowo.viewmodel.Message
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseRepositoryTest {

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val docRef: DocumentReference = mockk(relaxed = true)
    private lateinit var repository: FirebaseRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FirebaseRepository(firestore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVoidSuccessTask(result: Void?): Task<Void?> {
        val task = mockk<Task<Void?>>(relaxed = true)
        val successSlot = slot<OnSuccessListener<Void?>>()
        every { task.addOnSuccessListener(capture(successSlot)) } answers {
            successSlot.captured.onSuccess(result)
            task
        }
        val executorSlot = slot<Executor>()
        val successSlotWithExecutor = slot<OnSuccessListener<Void?>>()
        every { task.addOnSuccessListener(capture(executorSlot), capture(successSlotWithExecutor)) } answers {
            successSlotWithExecutor.captured.onSuccess(result)
            task
        }
        return task
    }

    @Test
    fun saveChatMessagesSucceeds() = runTest(testDispatcher) {
        val mockVoidTask = createVoidSuccessTask(null)
        val batch = mockk<WriteBatch>(relaxed = true)

        every { firestore.batch() } returns batch
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns mockVoidTask
        every { batch.commit() } returns mockVoidTask

        val messages = listOf(
            Message(id = 1L, text = "Hello", isUser = true),
            Message(id = 2L, text = "Hi there", isUser = false)
        )

        repository.saveChatMessages("user-1", "chat-123", messages, null)

        testScheduler.advanceUntilIdle()

        verify { batch.commit() }
    }
}