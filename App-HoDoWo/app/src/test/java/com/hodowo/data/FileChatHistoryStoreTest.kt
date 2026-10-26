package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.Message
import com.hodowo.viewmodel.SavedChat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

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

    // ---------- photos ----------

    /** Fake [Uri]: the Android class is only a stub in unit tests. */
    private fun fakeUri(text: String): Uri = mockk {
        every { this@mockk.toString() } returns text
    }

    private fun withFakeUris(block: suspend () -> Unit) = runTest {
        mockkStatic(Uri::class)
        try {
            every { Uri.fromFile(any()) } answers { fakeUri("file://" + firstArg<File>().absolutePath) }
            every { Uri.parse(any()) } answers { fakeUri(firstArg()) }
            block()
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    private fun chatWithPhoto(id: String, photo: Uri, updated: Long = 1_000) = SavedChat(
        id = id,
        title = "What is this button?",
        applianceName = "Washer",
        lastUpdated = updated,
        messages = listOf(
            Message(id = 1, text = "What is this button?", isUser = true, imageUri = photo),
            Message(id = 2, text = "It starts the wash.", isUser = false)
        )
    )

    private val photoFile get() = File(folder.root, "images/user-1__c1__1.jpg")

    @Test
    fun aPhotoIsCopiedSoTheHistoryStillShowsItAfterARestart() = withFakeUris {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val picked = fakeUri("content://gallery/42")
        val store = FileChatHistoryStore(folder.root, openImage = { ByteArrayInputStream(bytes) })

        store.save("user-1", chatWithPhoto("c1", picked))

        assertArrayEquals(bytes, photoFile.readBytes())
        val loaded = FileChatHistoryStore(folder.root).load("user-1").single()
        assertEquals("file://" + photoFile.absolutePath, loaded.messages[0].imageUri.toString())
        assertEquals(null, loaded.messages[1].imageUri)
    }

    @Test
    fun savingTheChatAgainKeepsTheFirstCopy() = withFakeUris {
        var opened = 0
        val store = FileChatHistoryStore(folder.root, openImage = {
            opened++
            ByteArrayInputStream(byteArrayOf(9))
        })

        store.save("user-1", chatWithPhoto("c1", fakeUri("content://gallery/42")))
        store.save("user-1", chatWithPhoto("c1", fakeUri("content://gallery/42"), updated = 2_000))

        assertEquals(1, opened)
        assertEquals(1, store.load("user-1").size)
    }

    @Test
    fun aPhotoThatCannotBeReadKeepsItsLink() = withFakeUris {
        val picked = fakeUri("content://gallery/42")
        val store = FileChatHistoryStore(folder.root, openImage = { null })

        val saved = chatWithPhoto("c1", picked)
        store.save("user-1", saved)

        assertEquals("content://gallery/42", store.load("user-1").single().messages[0].imageUri.toString())
        assertFalse(photoFile.exists())
    }

    @Test
    fun aReadErrorKeepsTheLinkToo() = withFakeUris {
        val store = FileChatHistoryStore(folder.root, openImage = { throw IOException("gone") })

        store.save("user-1", chatWithPhoto("c1", fakeUri("content://gallery/42")))

        assertEquals("content://gallery/42", store.load("user-1").single().messages[0].imageUri.toString())
    }

    @Test
    fun withoutOpenImageTheChatIsSavedAsItIs() = withFakeUris {
        val store = FileChatHistoryStore(folder.root)
        val chat = chatWithPhoto("c1", fakeUri("content://gallery/42"))

        store.save("user-1", chat)

        assertFalse(File(folder.root, "images").exists())
        val noPhoto = chat("c2", 2_000)
        store.save("user-1", noPhoto)
        assertSame(null, store.load("user-1").first().messages[0].imageUri)
    }

    @Test
    fun deletingAChatDeletesItsPhotos() = withFakeUris {
        val store = FileChatHistoryStore(folder.root, openImage = { ByteArrayInputStream(byteArrayOf(5)) })
        store.save("user-1", chatWithPhoto("c1", fakeUri("content://gallery/1")))
        store.save("user-1", chatWithPhoto("c10", fakeUri("content://gallery/10")))

        store.delete("user-1", "c1")

        assertFalse(photoFile.exists())
        // "c10" starts like "c1" but is another chat: its photo stays.
        assertTrue(File(folder.root, "images/user-1__c10__1.jpg").isFile)
    }

    @Test
    fun photosOfChatsDroppedFromTheHistoryAreDeleted() = withFakeUris {
        val store = FileChatHistoryStore(folder.root, openImage = { ByteArrayInputStream(byteArrayOf(5)) })

        store.save("user-1", chatWithPhoto("c1", fakeUri("content://gallery/1"), updated = 0))
        repeat(FileChatHistoryStore.MAX_CHATS) { index ->
            store.save("user-1", chat("new$index", index + 1L))
        }

        assertFalse(photoFile.exists())
    }
}
