package com.hodowo.data

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun <T> createFailureTask(exception: Exception): Task<T> {
        val task = mockk<Task<T>>(relaxed = true)
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
    fun saveManualGuideSucceeds() = runTest(testDispatcher) {
        val task = createSuccessTask<Void?>(null)
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val guide = ManualGuide(
            applianceName = "Washer",
            issueTitle = "E18",
            overview = "Clean filter",
            steps = listOf(ManualStep(1, "Step 1", "Clean it"))
        )

        repository.saveManualGuide("user-1", 100L, guide)
        testScheduler.advanceUntilIdle()

        verify { docRef.set(any(), any()) }
    }

    @Test
    fun saveManualGuideHandlesExceptionGracefully() = runTest(testDispatcher) {
        val task = createFailureTask<Void?>(RuntimeException("Firestore error"))
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val guide = ManualGuide(
            applianceName = "Washer",
            issueTitle = "E18",
            overview = "Clean filter"
        )

        repository.saveManualGuide("user-1", 100L, guide)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun saveBookmarkSucceeds() = runTest(testDispatcher) {
        val task = createSuccessTask<Void?>(null)
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val message = Message(
            id = 1L,
            text = "Fix guide",
            isUser = false,
            manualGuide = ManualGuide("Dryer", "E01", "Check lint")
        )

        repository.saveBookmark("user-1", message)
        testScheduler.advanceUntilIdle()

        verify { docRef.set(any(), any()) }
    }

    @Test
    fun saveBookmarkHandlesExceptionGracefully() = runTest(testDispatcher) {
        val task = createFailureTask<Void?>(RuntimeException("Firestore error"))
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val message = Message(id = 1L, text = "Fix guide", isUser = false)

        repository.saveBookmark("user-1", message)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun removeBookmarkSucceeds() = runTest(testDispatcher) {
        val task = createSuccessTask<Void?>(null)
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.delete() } returns task

        repository.removeBookmark("user-1", 100L)
        testScheduler.advanceUntilIdle()

        verify { docRef.delete() }
    }

    @Test
    fun removeBookmarkHandlesExceptionGracefully() = runTest(testDispatcher) {
        val task = createFailureTask<Void?>(RuntimeException("Delete failed"))
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.delete() } returns task

        repository.removeBookmark("user-1", 100L)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun getBookmarksReturnsListOfManualGuides() = runTest(testDispatcher) {
        val querySnapshot = mockk<QuerySnapshot>(relaxed = true)
        val docSnapshot = mockk<DocumentSnapshot>(relaxed = true)

        val stepsData = listOf(
            mapOf("number" to 1, "title" to "Step 1", "description" to "Clean filter")
        )
        every { docSnapshot.get("steps") } returns stepsData
        every { docSnapshot.getString("applianceName") } returns "Washing Machine"
        every { docSnapshot.getString("issueTitle") } returns "Drain Error"
        every { docSnapshot.getString("overview") } returns "Filter blocked"
        every { docSnapshot.getString("imageUrl") } returns "http://example.com/img.jpg"
        every { docSnapshot.getString("manualId") } returns "m-123"

        every { querySnapshot.documents } returns listOf(docSnapshot)

        val task = createSuccessTask(querySnapshot)
        every { firestore.collection(any()).document(any()).collection(any()).get() } returns task

        val bookmarks = repository.getBookmarks("user-1")
        testScheduler.advanceUntilIdle()

        assertEquals(1, bookmarks.size)
        assertEquals("Washing Machine", bookmarks[0].applianceName)
        assertEquals("Drain Error", bookmarks[0].issueTitle)
        assertEquals("Filter blocked", bookmarks[0].overview)
        assertEquals(1, bookmarks[0].steps.size)
        assertEquals("Clean filter", bookmarks[0].steps[0].description)
    }

    @Test
    fun getBookmarksHandlesExceptionReturnsEmptyList() = runTest(testDispatcher) {
        val task: Task<QuerySnapshot> = createFailureTask(RuntimeException("Fetch failed"))
        every { firestore.collection(any()).document(any()).collection(any()).get() } returns task

        val bookmarks = repository.getBookmarks("user-1")
        testScheduler.advanceUntilIdle()

        assertTrue(bookmarks.isEmpty())
    }

    @Test
    fun saveFeedbackSucceeds() = runTest(testDispatcher) {
        val task = createSuccessTask<Void?>(null)
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val report = FeedbackReport(
            messageId = 1L,
            appliance = Appliance("Washer", "Bosch", "V1", 0.9f),
            question = "How to clear error?",
            answer = "Clean filter",
            feedback = Feedback.LIKE,
            comment = "Great answer!"
        )

        repository.saveFeedback("user-1", report)
        testScheduler.advanceUntilIdle()

        verify(atLeast = 1) { docRef.set(any(), any()) }
    }

    @Test
    fun saveFeedbackHandlesExceptionGracefully() = runTest(testDispatcher) {
        val task = createFailureTask<Void?>(RuntimeException("Feedback failed"))
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val report = FeedbackReport(
            messageId = 1L,
            appliance = null,
            question = "q",
            answer = "a",
            feedback = Feedback.DISLIKE,
            comment = null
        )

        repository.saveFeedback("user-1", report)
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun saveChatMessagesSucceeds() = runTest(testDispatcher) {
        val mockVoidTask = createSuccessTask<Void?>(null)
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

    @Test
    fun saveChatMessagesHandlesExceptionGracefully() = runTest(testDispatcher) {
        val task = createFailureTask<Void?>(RuntimeException("Save failed"))
        every { firestore.collection(any()).document(any()) } returns docRef
        every { docRef.collection(any()).document(any()) } returns docRef
        every { docRef.set(any(), any()) } returns task

        val messages = listOf(Message(id = 1L, text = "Hi", isUser = true))

        repository.saveChatMessages("user-1", "chat-123", messages, null)
        testScheduler.advanceUntilIdle()
    }
}
