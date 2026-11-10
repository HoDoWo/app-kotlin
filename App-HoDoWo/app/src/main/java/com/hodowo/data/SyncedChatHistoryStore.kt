package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.ChatHistoryStore
import com.hodowo.viewmodel.SavedChat
import kotlinx.coroutines.CancellationException

/** The chats in the user's account and the ids of the chats deleted on any phone. */
data class AccountChats(
    val chats: List<SavedChat>,
    val deletedIds: Set<String> = emptySet()
)

/** The chats kept in the user's account (`users/{uid}/chats`), see [FirestoreChatHistory]. */
interface RemoteChatHistory {
    /** The user's chats, newest first, and the chats they deleted. */
    suspend fun list(userId: String): AccountChats

    /** Adds the chat, or replaces the one with the same id. Its photos are already online. */
    suspend fun save(userId: String, chat: SavedChat)

    /** Removes the chat and its photos, remembering that it was deleted. */
    suspend fun delete(userId: String, chatId: String)
}

/** Puts the photo of a chat message in the user's account and returns its https URL, or null. */
fun interface ChatImageUploader {
    suspend fun upload(userId: String, chatId: String, messageId: Long, uri: Uri): String?
}

/**
 * The chat history of a user, on the phone and in their account.
 *
 * * The phone copy ([local]) is always read and written first, so the history
 *   works offline and opens at once.
 * * The account copy ([remote]) follows the user to a new phone: [load] merges
 *   both (the newest version of each chat wins) and keeps the result on the phone.
 *   A chat deleted on another phone is deleted here too.
 * * Photos sent in a chat are uploaded once with [uploader]; the account copy
 *   points to the uploaded photo, the phone copy keeps its own file.
 *
 * Syncing is best effort: when the account cannot be reached the phone copy is
 * used, and the chats are sent again on the next save or load.
 */
class SyncedChatHistoryStore(
    private val local: ChatHistoryStore,
    private val remote: RemoteChatHistory,
    private val uploader: ChatImageUploader? = null,
    /** False for users without an account (guest): their chats stay on the phone. */
    private val syncs: (String) -> Boolean = { it !in LOCAL_ONLY_USERS }
) : ChatHistoryStore {

    /** Photos already online: "userId/chatId/messageId" -> https URL. */
    private val uploaded = mutableMapOf<String, String>()

    override suspend fun load(userId: String): List<SavedChat> {
        val onPhone = local.load(userId)
        if (!syncs(userId)) return onPhone

        val account = bestEffort { remote.list(userId) } ?: return onPhone
        val online = account.chats.filterNot { it.id in account.deletedIds }
        online.forEach { chat -> rememberUploads(userId, chat) }
        onPhone.filter { it.id in account.deletedIds }.forEach { local.delete(userId, it.id) }

        val phoneById = onPhone.filterNot { it.id in account.deletedIds }.associateBy { it.id }
        val onlineById = online.associateBy { it.id }
        val merged = (phoneById.keys + onlineById.keys).mapNotNull { id ->
            newest(userId, phoneById[id], onlineById[id])
        }
        return merged.sortedByDescending { it.lastUpdated }.take(FileChatHistoryStore.MAX_CHATS)
    }

    /** The newest copy of a chat; the other place gets it too. */
    private suspend fun newest(userId: String, phone: SavedChat?, account: SavedChat?): SavedChat? = when {
        account != null && (phone == null || account.lastUpdated > phone.lastUpdated) -> {
            local.save(userId, account)
            account
        }
        phone != null && (account == null || phone.lastUpdated > account.lastUpdated) -> {
            // Written on this phone while offline: send it now.
            bestEffort { remote.save(userId, withOnlineImages(userId, phone)) }
            phone
        }
        else -> phone
    }

    override suspend fun save(userId: String, chat: SavedChat) {
        local.save(userId, chat)
        if (syncs(userId)) {
            bestEffort { remote.save(userId, withOnlineImages(userId, chat)) }
        }
    }

    override suspend fun delete(userId: String, chatId: String) {
        local.delete(userId, chatId)
        uploaded.keys.removeAll { it.startsWith("$userId/$chatId/") }
        if (syncs(userId)) {
            bestEffort { remote.delete(userId, chatId) }
        }
    }

    /** The chat with every photo replaced by its uploaded copy (uploaded now if needed). */
    private suspend fun withOnlineImages(userId: String, chat: SavedChat): SavedChat {
        if (chat.messages.none { it.imageUri != null }) return chat
        return chat.copy(
            messages = chat.messages.map { message ->
                val uri = message.imageUri ?: return@map message
                if (uri.isOnline()) return@map message
                val key = "$userId/${chat.id}/${message.id}"
                val url = uploaded[key]
                    ?: bestEffort { uploader?.upload(userId, chat.id, message.id, uri) }?.also { uploaded[key] = it }
                // Not uploaded: no photo rather than a link that works only on this phone.
                message.copy(imageUri = url?.let(Uri::parse))
            }
        )
    }

    private fun rememberUploads(userId: String, chat: SavedChat) {
        chat.messages.forEach { message ->
            val uri = message.imageUri
            if (uri != null && uri.isOnline()) uploaded["$userId/${chat.id}/${message.id}"] = uri.toString()
        }
    }

    private fun Uri.isOnline(): Boolean = scheme == "https" || scheme == "http"

    private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Ids used when nobody is signed in: nothing to sync with. */
        val LOCAL_ONLY_USERS = setOf("guest", "anonymous", "")
    }
}
