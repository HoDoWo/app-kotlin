package com.hodowo.data

import android.net.Uri
import com.hodowo.viewmodel.ChatHistoryStore
import com.hodowo.viewmodel.Feedback
import com.hodowo.viewmodel.Message
import com.hodowo.viewmodel.SavedChat
import com.hodowo.viewmodel.upsert
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Keeps each user's chats in a JSON file in [directory] (the app's private
 * storage), for the chat history menu. Works offline and survives restarts.
 *
 * Only the newest [MAX_CHATS] chats and the last [MAX_MESSAGES] messages of
 * each chat are kept, so the file stays small.
 */
class FileChatHistoryStore(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatHistoryStore {

    private val mutex = Mutex()

    override suspend fun load(userId: String): List<SavedChat> = mutex.withLock {
        withContext(ioDispatcher) { read(userId) }
    }

    override suspend fun save(userId: String, chat: SavedChat) {
        mutex.withLock {
            withContext(ioDispatcher) {
                val chats = read(userId).upsert(chat).take(MAX_CHATS)
                writeAtomically(fileOf(userId), ChatJson.toJson(chats).toString())
            }
        }
    }

    override suspend fun delete(userId: String, chatId: String) {
        mutex.withLock {
            withContext(ioDispatcher) {
                val chats = read(userId).filterNot { it.id == chatId }
                writeAtomically(fileOf(userId), ChatJson.toJson(chats).toString())
            }
        }
    }

    private fun read(userId: String): List<SavedChat> {
        val file = fileOf(userId)
        if (!file.isFile) return emptyList()

        return try {
            ChatJson.parse(JSONObject(file.readText()))
        } catch (_: IOException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun fileOf(userId: String): File =
        File(directory, "chats_" + userId.replace(UNSAFE_CHARACTERS, "_") + ".json")

    companion object {
        const val MAX_CHATS = 50
        const val MAX_MESSAGES = 100
        private val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9_-]")
    }
}

/**
 * JSON form of the saved chats:
 * `{"chats": [{"id", "title", "appliance_name", "last_updated", "messages": [...]}]}`.
 * A message keeps its text, photo, rating, bookmark and guide (in the backend format).
 */
object ChatJson {

    fun toJson(chats: List<SavedChat>): JSONObject {
        val array = JSONArray()
        chats.forEach { chat ->
            val messages = JSONArray()
            chat.messages.takeLast(FileChatHistoryStore.MAX_MESSAGES).forEach { messages.put(messageToJson(it)) }
            array.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("appliance_name", chat.applianceName ?: JSONObject.NULL)
                    .put("last_updated", chat.lastUpdated)
                    .put("messages", messages)
            )
        }
        return JSONObject().put("chats", array)
    }

    /** Reads the chats; broken entries are skipped. */
    fun parse(payload: JSONObject): List<SavedChat> {
        val array = payload.optJSONArray("chats") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.textOrNull("id") ?: return@mapNotNull null
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()

            SavedChat(
                id = id,
                title = item.optString("title").ifBlank { "Chat" },
                applianceName = item.textOrNull("appliance_name"),
                lastUpdated = item.optLong("last_updated"),
                messages = (0 until messagesJson.length()).mapNotNull { i ->
                    messagesJson.optJSONObject(i)?.let(::messageFromJson)
                }
            )
        }
    }

    private fun messageToJson(message: Message): JSONObject =
        JSONObject()
            .put("id", message.id)
            .put("text", message.text ?: JSONObject.NULL)
            .put("image_uri", message.imageUri?.toString() ?: JSONObject.NULL)
            .put("is_user", message.isUser)
            .put("timestamp", message.timestamp)
            .put("question", message.question ?: JSONObject.NULL)
            .put("feedback", message.feedback?.name ?: JSONObject.NULL)
            .put("feedback_comment", message.feedbackComment ?: JSONObject.NULL)
            .put("is_bookmarked", message.isBookmarked)
            .put("guide", message.manualGuide?.let(ManualJson::guideToJson) ?: JSONObject.NULL)

    private fun messageFromJson(item: JSONObject): Message? {
        if (!item.has("id")) return null

        return Message(
            id = item.optLong("id"),
            text = if (item.isNull("text")) null else item.optString("text"),
            imageUri = item.textOrNull("image_uri")?.let(Uri::parse),
            isUser = item.optBoolean("is_user"),
            timestamp = item.optLong("timestamp"),
            question = item.textOrNull("question"),
            feedback = item.textOrNull("feedback")?.let { name -> Feedback.entries.firstOrNull { it.name == name } },
            feedbackComment = item.textOrNull("feedback_comment"),
            manualGuide = item.optJSONObject("guide")?.let(ManualJson::guideFromJson),
            isBookmarked = item.optBoolean("is_bookmarked")
        )
    }
}
