package com.hodowo.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.Message
import kotlinx.coroutines.tasks.await

class FirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "FirebaseRepository"
    }

    /**
     * Saves an AI manual guide response under the user's collection.
     */
    suspend fun saveManualGuide(userId: String, messageId: Long, guide: ManualGuide) {
        try {
            val data = hashMapOf(
                "messageId" to messageId,
                "manualId" to (guide.manualId ?: ""),
                "applianceName" to guide.applianceName,
                "issueTitle" to guide.issueTitle,
                "overview" to guide.overview,
                "imageUrl" to guide.imageUrl,
                "warnings" to guide.warnings,
                "steps" to guide.steps.map {
                    mapOf(
                        "number" to it.number,
                        "title" to it.title,
                        "description" to it.description
                    )
                },
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(userId)
                .collection("manuals")
                .document(messageId.toString())
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Saved manual guide $messageId for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save manual guide to Firebase: ${e.message}")
        }
    }

    /**
     * Saves a bookmark for a message/manual.
     */
    suspend fun saveBookmark(userId: String, message: Message) {
        try {
            val guide = message.manualGuide
            val data = hashMapOf(
                "messageId" to message.id,
                "manualId" to (guide?.manualId ?: ""),
                "text" to (message.text ?: ""),
                "applianceName" to (guide?.applianceName ?: ""),
                "issueTitle" to (guide?.issueTitle ?: (message.question ?: "Appliance Guide")),
                "overview" to (guide?.overview ?: (message.text ?: "")),
                "imageUrl" to (guide?.imageUrl ?: ""),
                "steps" to (guide?.steps?.map {
                    mapOf("number" to it.number, "title" to it.title, "description" to it.description)
                } ?: emptyList<Map<String, Any>>()),
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .document(message.id.toString())
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Saved bookmark ${message.id} for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save bookmark to Firebase: ${e.message}")
        }
    }

    /**
     * Removes a bookmark.
     */
    suspend fun removeBookmark(userId: String, messageId: Long) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .document(messageId.toString())
                .delete()
                .await()
            Log.d(TAG, "Removed bookmark $messageId for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove bookmark: ${e.message}")
        }
    }

    /**
     * Loads bookmarked manuals for the user.
     */
    suspend fun getBookmarks(userId: String): List<ManualGuide> {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val stepsList = (doc.get("steps") as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    ManualStep(
                        number = (map["number"] as? Number)?.toInt() ?: 1,
                        title = map["title"] as? String ?: "",
                        description = map["description"] as? String ?: ""
                    )
                } ?: emptyList()

                ManualGuide(
                    applianceName = doc.getString("applianceName") ?: "Appliance",
                    issueTitle = doc.getString("issueTitle") ?: "Repair Guide",
                    overview = doc.getString("overview") ?: "",
                    steps = stepsList,
                    imageUrl = doc.getString("imageUrl"),
                    manualId = doc.getString("manualId")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch bookmarks: ${e.message}")
            emptyList()
        }
    }

    /**
     * Saves user feedback/rating to Firebase.
     */
    suspend fun saveFeedback(userId: String, report: FeedbackReport) {
        try {
            val data = hashMapOf(
                "userId" to userId,
                "messageId" to report.messageId,
                "rating" to report.feedback.score,
                "question" to report.question,
                "answer" to report.answer,
                "comment" to (report.comment ?: ""),
                "applianceName" to (report.appliance?.name ?: ""),
                "applianceBrand" to (report.appliance?.brand ?: ""),
                "timestamp" to report.timestamp
            )

            // Save under user's ratings
            firestore.collection("users")
                .document(userId)
                .collection("ratings")
                .document(report.messageId.toString())
                .set(data, SetOptions.merge())
                .await()

            // Also record in global ratings collection
            firestore.collection("ratings")
                .document("${userId}_${report.messageId}")
                .set(data, SetOptions.merge())
                .await()

            Log.d(TAG, "Saved feedback for message ${report.messageId}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save feedback to Firebase: ${e.message}")
        }
    }

    /**
     * Saves chat session and its messages to Firebase.
     */
    suspend fun saveChatMessages(userId: String, chatId: String, messages: List<Message>, appliance: Appliance?) {
        try {
            val chatDoc = hashMapOf(
                "chatId" to chatId,
                "lastUpdated" to System.currentTimeMillis(),
                "applianceName" to (appliance?.name ?: ""),
                "applianceBrand" to (appliance?.brand ?: "")
            )

            firestore.collection("users")
                .document(userId)
                .collection("chats")
                .document(chatId)
                .set(chatDoc, SetOptions.merge())
                .await()

            // Save individual messages
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userId)
                .collection("chats")
                .document(chatId)
                .collection("messages")

            messages.takeLast(30).forEach { msg ->
                val msgDoc = hashMapOf(
                    "id" to msg.id,
                    "text" to (msg.text ?: ""),
                    "isUser" to msg.isUser,
                    "timestamp" to msg.timestamp,
                    "question" to (msg.question ?: ""),
                    "feedback" to msg.feedback?.name,
                    "isBookmarked" to msg.isBookmarked,
                    "hasManual" to (msg.manualGuide != null)
                )
                batch.set(collectionRef.document(msg.id.toString()), msgDoc, SetOptions.merge())
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist chat messages to Firebase: ${e.message}")
        }
    }
}
