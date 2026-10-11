package com.hodowo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkeletonViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun blankMessageShowsValidationError() {
        val viewModel = SkeletonViewModel(ioDispatcher = dispatcher)

        viewModel.send("   ")

        assertEquals("Escribe un mensaje.", viewModel.reply)
    }

    @Test
    fun successfulRequestShowsReply() = runTest(dispatcher) {
        val viewModel = SkeletonViewModel(
            chatClient = ChatClient { "Hello from the LLM" },
            ioDispatcher = dispatcher,
        )

        viewModel.send("Hello")
        advanceUntilIdle()

        assertEquals("Hello from the LLM", viewModel.reply)
    }

    @Test
    fun failedRequestShowsError() = runTest(dispatcher) {
        val viewModel = SkeletonViewModel(
            chatClient = ChatClient { error("network unavailable") },
            ioDispatcher = dispatcher,
        )

        viewModel.send("Hello")
        advanceUntilIdle()

        assertEquals("Error: network unavailable", viewModel.reply)
    }

}
