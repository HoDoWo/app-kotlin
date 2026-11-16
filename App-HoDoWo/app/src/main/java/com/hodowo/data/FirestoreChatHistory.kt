package com.hodowo.data

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.hodowo.viewmodel.SavedChat
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The chats in the user's account: one document per chat in `users/{uid}/chats`,
 * with the whole chat as JSON in `payload` (the same format as the phone copy)
 * and `last_updated` to list them newest first.
 *
 * A deleted chat leaves `{deleted: true}`, so the other phones remove it too.
 * Chat documents written by older versions have no `payload` and are skipped.
 *
 * Reading asks the server (offline it fails, and the phone copy is used).
 * Writing does not wait for the server: Firestore keeps the write on the phone
 * and sends it as soon as there is a connection, even after a restart.
 */
class FirestoreChatHistory(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : RemoteChatHistory {

    override suspend fun list(userId: String): AccountChats {
        val documents = chats(userId)
            .orderBy(LAST_UPDATED, Query.Direction.DESCENDING)
            .limit(LISTED.toLong())
            .get(Source.SERVER)
            .await()
            .documents
        val deleted = documents.filter { it.getBoolean(DELETED) == true }.map { it.id }.toSet()
        val chats = documents.mapNotNull { document ->
            val payload = document.getString(PAYLOAD) ?: return@mapNotNull null
            try {
                ChatJson.chatFromJson(JSONObject(payload))
            } catch (_: JSONException) {
                null
            }
        }
        return AccountChats(chats.take(FileChatHistoryStore.MAX_CHATS), deleted)
    }

    override suspend fun save(userId: String, chat: SavedChat) {
        val data = hashMapOf<String, Any?>(
            PAYLOAD to ChatJson.compactChat(chat),
            "title" to chat.title,
            "appliance_name" to chat.applianceName,
            LAST_UPDATED to chat.lastUpdated
        )
        chats(userId).document(chat.id).set(data)
    }

    override suspend fun delete(userId: String, chatId: String) {
        // A marker instead of nothing, so the other phones delete their copy too.
        chats(userId).document(chatId).set(mapOf(DELETED to true, LAST_UPDATED to System.currentTimeMillis()))
        val photos = storage.reference.child(chatImagesPath(userId, chatId)).listAll().await()
        photos.items.forEach { it.delete().await() }
    }

    private fun chats(userId: String) = firestore.collection("users").document(userId).collection("chats")

    companion object {
        private const val PAYLOAD = "payload"
        private const val LAST_UPDATED = "last_updated"
        private const val DELETED = "deleted"

        /** The newest chats and deletion markers read at once. */
        private const val LISTED = 100

        fun chatImagesPath(userId: String, chatId: String) = "users/$userId/chats/$chatId"
    }
}

/**
 * Uploads a chat photo to `users/{uid}/chats/{chatId}/{messageId}.jpg`, made
 * smaller first with [compress], and returns its download URL.
 */
class FirebaseChatImageUploader(
    private val compress: suspend (Uri) -> ByteArray,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : ChatImageUploader {

    init {
        // Offline the upload fails quickly and is tried again on the next save.
        storage.maxUploadRetryTimeMillis = RETRY_MILLIS
        storage.maxOperationRetryTimeMillis = RETRY_MILLIS
    }

    override suspend fun upload(userId: String, chatId: String, messageId: Long, uri: Uri): String? {
        val ref = storage.reference.child("${FirestoreChatHistory.chatImagesPath(userId, chatId)}/$messageId.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(compress(uri), metadata).await()
        return ref.downloadUrl.await().toString()
    }

    private companion object {
        const val RETRY_MILLIS = 15_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
