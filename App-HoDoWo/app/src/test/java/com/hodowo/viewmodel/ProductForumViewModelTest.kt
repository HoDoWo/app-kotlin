package com.hodowo.viewmodel

import com.hodowo.data.ForumException
import com.hodowo.data.ForumReply
import com.hodowo.data.ForumReplyPage
import com.hodowo.data.ForumReportReason
import com.hodowo.data.ForumService
import com.hodowo.data.ForumThread
import com.hodowo.data.ForumVote
import com.hodowo.data.ProductThreads
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun reply(id: String, mine: Boolean = false) = ForumReply(
    id = id, body = "Reply $id", authorName = "Anna", authorBadge = null, createdAt = 1L,
    upvotes = 0, votedByMe = false, isMine = mine
)

private fun thread(id: String, replies: Int = 0, mine: Boolean = false, top: ForumReply? = null) = ForumThread(
    id = id, title = "Question $id here?", body = null, authorName = "Davide", authorBadge = null,
    createdAt = 1L, lastActivityAt = 1L, upvotes = 0, replyCount = replies, votedByMe = false,
    isMine = mine, topReply = top
)

/** Unit tests for [ProductForumViewModel] (app #150): loading, posting, replies, votes, reports and errors. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductForumViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeForumService

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeForumService()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProductForumViewModel(service, "part", "pump-1")

    // ---------- loading ----------

    @Test
    fun loadingShowsTheQuestionsOfTheProduct() = runTest(dispatcher) {
        service.threads = listOf(thread("t1", replies = 1, top = reply("r1")), thread("t2"))
        val vm = viewModel()

        assertTrue(vm.uiState.value.isLoading)
        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("t1", "t2"), state.threads.map { it.id })
        assertEquals(2, state.total)
        assertEquals(listOf("part" to "pump-1"), service.productCalls)
    }

    @Test
    fun aProductWithoutQuestionsIsEmpty() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.threads.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun loadingErrorsAreShownAndCanBeRetried() = runTest(dispatcher) {
        service.error = IOException("offline")
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        assertEquals("Could not load the questions, check your connection", vm.uiState.value.error)

        service.error = ForumException("the forum is unavailable")
        vm.load()
        advanceUntilIdle()
        assertEquals("the forum is unavailable", vm.uiState.value.error)

        service.error = IOException("Not authorized: please sign in again")
        vm.load()
        advanceUntilIdle()
        assertEquals("Not authorized: please sign in again", vm.uiState.value.error)

        service.error = null
        vm.load()
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
    }

    // ---------- asking ----------

    @Test
    fun askingPostsTheQuestionAndShowsItFirst() = runTest(dispatcher) {
        service.threads = listOf(thread("old"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        vm.startAsking()
        vm.onTitleChange("  Does it include the seal?  ")
        vm.onDetailsChange("   ")
        vm.submitQuestion()
        advanceUntilIdle()

        assertEquals(listOf(Triple("Does it include the seal?", null as String?, "part:pump-1")), service.asked)
        val state = vm.uiState.value
        assertNull(state.asking)
        assertEquals(listOf("new-thread", "old"), state.threads.map { it.id })
        assertEquals(2, state.total)
        assertEquals("Question posted", state.message)

        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun aTooShortQuestionIsNotSent() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startAsking()
        vm.onTitleChange("Seal?")
        vm.submitQuestion()
        advanceUntilIdle()

        assertEquals("Write a question of at least 8 characters", vm.uiState.value.asking?.error)
        assertTrue(service.asked.isEmpty())

        vm.onTitleChange("Does it include the seal?")
        assertNull(vm.uiState.value.asking?.error)
        vm.cancelAsking()
        assertNull(vm.uiState.value.asking)
    }

    @Test
    fun aRefusedQuestionKeepsTheComposerOpenWithTheReason() = runTest(dispatcher) {
        service.error = ForumException("You are posting too fast, wait a minute")
        val vm = viewModel()
        vm.startAsking()
        vm.onTitleChange("Does it include the seal?")
        vm.submitQuestion()
        advanceUntilIdle()

        val asking = vm.uiState.value.asking
        assertEquals("You are posting too fast, wait a minute", asking?.error)
        assertEquals("Does it include the seal?", asking?.title)
        assertFalse(asking!!.isSending)
    }

    @Test
    fun aSecondTapWhileSendingIsIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        service.askGate = gate
        val vm = viewModel()
        vm.startAsking()
        vm.onTitleChange("Does it include the seal?")
        vm.submitQuestion()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.asking!!.isSending)

        vm.submitQuestion()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, service.asked.size)
    }

    @Test
    fun theQuestionLimitsMatchTheBackend() {
        assertNull(ProductForumViewModel.questionError("Exactly8", ""))
        assertEquals("The question can be at most 150 characters", ProductForumViewModel.questionError("a".repeat(151), ""))
        assertEquals(
            "The details can be at most 2000 characters",
            ProductForumViewModel.questionError("A good question?", "x".repeat(2001))
        )
        assertEquals("Write a reply of at least 2 characters", ProductForumViewModel.replyError(" a "))
        assertEquals("The reply can be at most 2000 characters", ProductForumViewModel.replyError("x".repeat(2001)))
        assertNull(ProductForumViewModel.replyError("ok"))
    }

    // ---------- thread and replies ----------

    @Test
    fun openingAThreadLoadsItsRepliesPageByPage() = runTest(dispatcher) {
        service.pages = mapOf(
            null to ForumReplyPage(listOf(reply("r1"), reply("r2")), nextCursor = "r2"),
            "r2" to ForumReplyPage(listOf(reply("r3")), nextCursor = null)
        )
        val vm = viewModel()
        vm.openThread(thread("t1", replies = 3))
        advanceUntilIdle()

        var open = vm.uiState.value.openThread!!
        assertEquals(listOf("r1", "r2"), open.replies.map { it.id })
        assertTrue(open.canLoadMore)

        vm.loadMoreReplies()
        advanceUntilIdle()
        open = vm.uiState.value.openThread!!
        assertEquals(listOf("r1", "r2", "r3"), open.replies.map { it.id })
        assertFalse(open.canLoadMore)

        vm.loadMoreReplies() // last page: nothing more to ask
        advanceUntilIdle()
        assertEquals(listOf(null, "r2"), service.replyCursors)

        vm.closeThread()
        assertNull(vm.uiState.value.openThread)
    }

    @Test
    fun aRepliesErrorIsShownInTheThread() = runTest(dispatcher) {
        service.error = IOException("offline")
        val vm = viewModel()
        vm.openThread(thread("t1"))
        advanceUntilIdle()

        val open = vm.uiState.value.openThread!!
        assertEquals("Could not load the replies, check your connection", open.error)
        assertFalse(open.isLoading)
    }

    @Test
    fun replyingAddsTheReplyAndUpdatesTheCounts() = runTest(dispatcher) {
        service.threads = listOf(thread("t1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.openThread(vm.uiState.value.threads.first())
        advanceUntilIdle()

        vm.onReplyDraftChange("  Yes, it is in the box.  ")
        vm.sendReply()
        advanceUntilIdle()

        assertEquals(listOf("t1" to "Yes, it is in the box."), service.replied)
        val open = vm.uiState.value.openThread!!
        assertEquals(listOf("new-reply"), open.replies.map { it.id })
        assertEquals("", open.replyDraft)
        assertEquals(1, open.thread.replyCount)
        val listed = vm.uiState.value.threads.first()
        assertEquals(1, listed.replyCount)
        assertEquals("new-reply", listed.topReply?.id)
    }

    @Test
    fun anEmptyReplyIsNotSentAndAFailedOneKeepsTheText() = runTest(dispatcher) {
        val vm = viewModel()
        vm.openThread(thread("t1"))
        advanceUntilIdle()

        vm.sendReply()
        assertEquals("Write a reply of at least 2 characters", vm.uiState.value.openThread?.replyError)

        service.error = ForumException("thread not found")
        vm.onReplyDraftChange("Check the filter")
        assertNull(vm.uiState.value.openThread?.replyError)
        vm.sendReply()
        advanceUntilIdle()

        val open = vm.uiState.value.openThread!!
        assertEquals("thread not found", open.replyError)
        assertEquals("Check the filter", open.replyDraft)
        assertTrue(service.replied.isEmpty())
    }

    // ---------- votes and reports ----------

    @Test
    fun votingTogglesTheUpvoteOnTheListAndTheOpenThread() = runTest(dispatcher) {
        service.threads = listOf(thread("t1"))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.openThread(vm.uiState.value.threads.first())
        advanceUntilIdle()

        vm.toggleVote(vm.uiState.value.threads.first())
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.threads.first().upvotes)
        assertTrue(vm.uiState.value.openThread!!.thread.votedByMe)

        vm.toggleVote(vm.uiState.value.threads.first())
        advanceUntilIdle()
        assertEquals(listOf(Triple("t1", null as String?, true), Triple("t1", null, false)), service.votes)
        assertFalse(vm.uiState.value.threads.first().votedByMe)
    }

    @Test
    fun votingForAReplyUpdatesItEverywhere() = runTest(dispatcher) {
        val top = reply("r1")
        service.threads = listOf(thread("t1", replies = 1, top = top))
        service.pages = mapOf(null to ForumReplyPage(listOf(top), null))
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.openThread(vm.uiState.value.threads.first())
        advanceUntilIdle()

        vm.toggleVote("t1", top)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.threads.first().topReply!!.votedByMe)
        assertEquals(1, vm.uiState.value.openThread!!.replies.first().upvotes)
    }

    @Test
    fun ownPostsCannotBeUpvoted() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleVote(thread("t1", mine = true))
        vm.toggleVote("t1", reply("r1", mine = true))
        advanceUntilIdle()

        assertTrue(service.votes.isEmpty())
    }

    @Test
    fun aVoteErrorIsShownAsAMessage() = runTest(dispatcher) {
        service.error = ForumException("you cannot upvote your own post")
        val vm = viewModel()
        vm.toggleVote(thread("t1"))
        advanceUntilIdle()
        assertEquals("you cannot upvote your own post", vm.uiState.value.message)

        service.error = IOException("offline")
        vm.toggleVote("t1", reply("r1"))
        advanceUntilIdle()
        assertEquals("Could not save your vote, check your connection", vm.uiState.value.message)
    }

    @Test
    fun reportingThanksTheUserOrShowsTheError() = runTest(dispatcher) {
        val vm = viewModel()
        vm.report("t1", "r1", ForumReportReason.UNSAFE)
        advanceUntilIdle()
        assertEquals(listOf(Triple("t1", "r1" as String?, ForumReportReason.UNSAFE)), service.reports)
        assertEquals("Thanks, a moderator will look at it", vm.uiState.value.message)

        service.error = IOException("offline")
        vm.report("t1", null, ForumReportReason.SPAM)
        advanceUntilIdle()
        assertEquals("Could not send the report, check your connection", vm.uiState.value.message)
    }
}

/** In-memory [ForumService] that records every call. */
private class FakeForumService : ForumService {
    var threads: List<ForumThread> = emptyList()
    var pages: Map<String?, ForumReplyPage> = emptyMap()
    var error: IOException? = null
    var askGate: CompletableDeferred<Unit>? = null

    val productCalls = mutableListOf<Pair<String, String>>()
    val asked = mutableListOf<Triple<String, String?, String>>()
    val replied = mutableListOf<Pair<String, String>>()
    val replyCursors = mutableListOf<String?>()
    val votes = mutableListOf<Triple<String, String?, Boolean>>()
    val reports = mutableListOf<Triple<String, String?, ForumReportReason>>()

    private fun fail() {
        error?.let { throw it }
    }

    override suspend fun productThreads(productType: String, productId: String, sort: String): ProductThreads {
        fail()
        productCalls.add(productType to productId)
        return ProductThreads(threads, threads.size)
    }

    override suspend fun replies(threadId: String, cursor: String?, limit: Int): ForumReplyPage {
        fail()
        replyCursors.add(cursor)
        return pages[cursor] ?: ForumReplyPage(emptyList(), null)
    }

    override suspend fun askAboutProduct(productType: String, productId: String, title: String, body: String?): ForumThread {
        askGate?.await()
        fail()
        asked.add(Triple(title, body, "$productType:$productId"))
        return thread("new-thread", mine = true).copy(title = title)
    }

    override suspend fun reply(threadId: String, body: String): ForumReply {
        fail()
        replied.add(threadId to body)
        return reply("new-reply", mine = true).copy(body = body)
    }

    override suspend fun vote(threadId: String, replyId: String?, up: Boolean): ForumVote {
        fail()
        votes.add(Triple(threadId, replyId, up))
        return ForumVote(if (up) 1 else 0, up)
    }

    override suspend fun report(threadId: String, replyId: String?, reason: ForumReportReason) {
        fail()
        reports.add(Triple(threadId, replyId, reason))
    }
}