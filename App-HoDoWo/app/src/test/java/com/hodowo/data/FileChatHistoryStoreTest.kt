package com.hodowo.data

import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.Message
import com.hodowo.viewmodel.SavedChat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The chats kept on the phone for the history menu. */
class FileChatHistoryStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun chat(id: String, updated: Long, question: String = "How do I set 180 °C?") = SavedChat(
        id = id,
        title = question,
        applianceName = "Indesit Oven",
        lastUpdated = updated,
        messages = listOf(
            Message(id = 1, text = question, isUser = true, timestamp = updated),
            Message(
                id = 2,
                text = "Turn the right knob.",
                isUser = false,
                timestamp = updated,
                question = question,
                feedback = Feedback.LIKE,
                feedbackComment = "clear",
                isBookmarked = true,
                manualGuide = ManualGuide(
                    applianceName = "Indesit Oven",
                    issueTitle = question,
                    overview = "Use the right knob.",
                    imageUrl = "https://img.test/oven.png",
                    manualId = "m1"
                )
            )
        )
    )

    @Test
    fun aChatIsGivenBackWithAllItsMessages() = runTest {
        val store = FileChatHistoryStore(folder.root)

        store.save("user-1", chat("c1", 1_000))

        assertEquals(listOf(chat("c1", 1_000)), store.load("user-1"))
    }

    @Test
    fun chatsAreNewestFirstAndSavingAgainReplacesTheChat() = runTest {
        val store = FileChatHistoryStore(folder.root)

        store.save("user-1", chat("c1", 1_000))
        store.save("user-1", chat("c2", 2_000))
        store.save("user-1", chat("c1", 3_000, question = "And with the grill?"))

        val chats = store.load("user-1")
        assertEquals(listOf("c1", "c2"), chats.map { it.id })
        assertEquals("And with the grill?", chats.first().title)
    }

    @Test
    fun aDeletedChatIsGone() = runTest {
        val store = FileChatHistoryStore(folder.root)
        store.save("user-1", chat("c1", 1_000))
        store.save("user-1", chat("c2", 2_000))

        store.delete("user-1", "c1")

        assertEquals(listOf("c2"), store.load("user-1").map { it.id })
    }

    @Test
    fun everyUserHasTheirOwnChats() = runTest {
        val store = FileChatHistoryStore(folder.root)

        store.save("user-1", chat("c1", 1_000))

        assertTrue(store.load("user-2").isEmpty())
    }

    @Test
    fun onlyTheNewestChatsAreKept() = runTest {
        val store = FileChatHistoryStore(folder.root)

        repeat(FileChatHistoryStore.MAX_CHATS + 5) { index ->
            store.save("user-1", chat("c$index", index.toLong()))
        }

        val chats = store.load("user-1")
        assertEquals(FileChatHistoryStore.MAX_CHATS, chats.size)
        assertEquals("c${FileChatHistoryStore.MAX_CHATS + 4}", chats.first().id)
    }

    @Test
    fun aBrokenFileMeansNoChats() = runTest {
        folder.newFile("chats_user-1.json").writeText("broken")

        assertTrue(FileChatHistoryStore(folder.root).load("user-1").isEmpty())
    }
}
