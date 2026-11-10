package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.ChatHistoryStore
import com.hodowo.viewmodel.Message
import com.hodowo.viewmodel.SavedChat
import com.hodowo.viewmodel.upsert
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The chat history on the phone and in the user's account. */
class SyncedChatHistoryStoreTest {

    private val phone = MemoryStore()
    private val account = MemoryRemote()
    private val uploads = mutableListOf<String>()
    private val uploader = ChatImageUploader { _, chatId, messageId, _ ->
        uploads += "$chatId/$messageId"
        "https://storage.test/$chatId/$messageId.jpg"
    }
    private val store = SyncedChatHistoryStore(phone, account, uploader)

    @Before
    fun fakeUris() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { uri(firstArg()) }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun uri(text: String): Uri = mockk {
        every { scheme } returns text.substringBefore(":")
        every { this@mockk.toString() } returns text
    }

    private fun chat(id: String, updated: Long, photo: Uri? = null) = SavedChat(
        id = id,
        title = "Question $id",
        applianceName = "Indesit Oven",
        lastUpdated = updated,
        messages = listOf(Message(id = 1, text = "Question $id", imageUri = photo, isUser = true, timestamp = updated))
    )

    @Test
    fun aSavedChatGoesToThePhoneAndToTheAccount() = runTest {
        store.save("user-1", chat("c1", 1_000))

        assertEquals(listOf("c1"), phone.chats.map { it.id })
        assertEquals(listOf("c1"), account.chats.map { it.id })
    }

    @Test
    fun onANewPhoneTheChatsComeFromTheAccountAndAreKeptOnThePhone() = runTest {
        account.chats = listOf(chat("c2", 2_000), chat("c1", 1_000))

        val chats = store.load("user-1")

        assertEquals(listOf("c2", "c1"), chats.map { it.id })
        assertEquals(listOf("c2", "c1"), phone.chats.map { it.id })
    }

    @Test
    fun theNewestVersionOfAChatWinsAndAChatWrittenOfflineIsSent() = runTest {
        phone.chats = listOf(chat("offline", 3_000), chat("old-here", 1_000))
        account.chats = listOf(chat("old-here", 2_000))

        val chats = store.load("user-1")

        assertEquals(listOf("offline" to 3_000L, "old-here" to 2_000L), chats.map { it.id to it.lastUpdated })
        assertEquals(2_000L, phone.chats.first { it.id == "old-here" }.lastUpdated)
        assertTrue(account.chats.any { it.id == "offline" })
    }

    @Test
    fun withoutConnectionThePhoneCopyIsUsed() = runTest {
        phone.chats = listOf(chat("c1", 1_000))
        account.fail = true

        assertEquals(listOf("c1"), store.load("user-1").map { it.id })
        store.save("user-1", chat("c2", 2_000)) // does not throw
        assertEquals(listOf("c2", "c1"), phone.chats.map { it.id })
    }

    @Test
    fun aGuestKeepsTheChatsOnlyOnThePhone() = runTest {
        store.save("guest", chat("c1", 1_000))

        assertEquals(1, phone.chats.size)
        assertTrue(account.chats.isEmpty())
    }

    @Test
    fun photosAreUploadedOnceAndTheAccountPointsToTheUploadedCopy() = runTest {
        val photo = uri("content://media/42")

        store.save("user-1", chat("c1", 1_000, photo))
        store.save("user-1", chat("c1", 2_000, photo))

        assertEquals(listOf("c1/1"), uploads)
        assertEquals("https://storage.test/c1/1.jpg", account.chats.single().messages.single().imageUri.toString())
        // The phone keeps its own photo.
        assertEquals("content://media/42", phone.chats.single().messages.single().imageUri.toString())
    }

    @Test
    fun photosAlreadyOnlineAreNotUploadedAgain() = runTest {
        account.chats = listOf(chat("c1", 1_000, uri("https://storage.test/c1/1.jpg")))

        store.load("user-1")
        store.save("user-1", chat("c1", 2_000, uri("file:///data/c1_1.jpg")))

        assertTrue(uploads.isEmpty())
        assertEquals("https://storage.test/c1/1.jpg", account.chats.single().messages.single().imageUri.toString())
    }

    @Test
    fun aPhotoThatCannotBeUploadedIsLeftOutOfTheAccountCopy() = runTest {
        val failing = SyncedChatHistoryStore(phone, account, { _, _, _, _ -> throw IOException("offline") })

        failing.save("user-1", chat("c1", 1_000, uri("content://media/42")))

        assertNull(account.chats.single().messages.single().imageUri)
        assertEquals("content://media/42", phone.chats.single().messages.single().imageUri.toString())
    }

    @Test
    fun aChatDeletedOnAnotherPhoneIsDeletedHereTooAndNotSentBack() = runTest {
        phone.chats = listOf(chat("gone", 2_000), chat("kept", 1_000))
        account.chats = listOf(chat("kept", 1_000))
        account.deleted = setOf("gone")

        assertEquals(listOf("kept"), store.load("user-1").map { it.id })
        assertEquals(listOf("kept"), phone.chats.map { it.id })
        assertEquals(listOf("kept"), account.chats.map { it.id })
    }

    @Test
    fun deletingRemovesTheChatEverywhere() = runTest {
        store.save("user-1", chat("c1", 1_000))

        store.delete("user-1", "c1")

        assertTrue(phone.chats.isEmpty())
        assertTrue(account.chats.isEmpty())
    }
}

private class MemoryStore : ChatHistoryStore {
    var chats: List<SavedChat> = emptyList()

    override suspend fun load(userId: String) = chats
    override suspend fun save(userId: String, chat: SavedChat) {
        chats = chats.upsert(chat)
    }
    override suspend fun delete(userId: String, chatId: String) {
        chats = chats.filterNot { it.id == chatId }
    }
}

private class MemoryRemote : RemoteChatHistory {
    var chats: List<SavedChat> = emptyList()
    var fail = false

    var deleted: Set<String> = emptySet()

    override suspend fun list(userId: String): AccountChats {
        if (fail) throw IOException("offline")
        return AccountChats(chats, deleted)
    }
    override suspend fun save(userId: String, chat: SavedChat) {
        if (fail) throw IOException("offline")
        chats = chats.upsert(chat)
    }
    override suspend fun delete(userId: String, chatId: String) {
        if (fail) throw IOException("offline")
        chats = chats.filterNot { it.id == chatId }
        deleted = deleted + chatId
    }
}
