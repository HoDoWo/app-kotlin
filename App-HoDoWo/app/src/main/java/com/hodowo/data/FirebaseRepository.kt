package com.hodowo.data

import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.hodowo.viewmodel.Appliance
import com.hodowo.viewmodel.FeedbackReport
import com.hodowo.viewmodel.ManualGuide
import com.hodowo.viewmodel.ManualStep
import com.hodowo.viewmodel.Message
import com.hodowo.viewmodel.UserAppliance
import com.hodowo.viewmodel.UserProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "FirebaseRepository"
    }

    /**
     * Saves an AI manual guide response under the user's collection.
     */
    suspend fun saveManualGuide(userId: String, messageId: Long, guide: ManualGuide, applianceId: String? = null) {
        try {
            val data = hashMapOf(
                "messageId" to messageId,
                "manualId" to (guide.manualId ?: ""),
                "applianceId" to (applianceId ?: ""),
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
     * Removes a bookmark by manual ID (e.g. from Profile screen).
     */
    suspend fun removeBookmarkByManualId(userId: String, manualId: String) {
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .whereEqualTo("manualId", manualId)
                .get()
                .await()
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
            Log.d(TAG, "Removed bookmark by manualId $manualId for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove bookmark by manualId: ${e.message}")
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

    /**
     * Saves or updates the user profile in Firestore under users/{userId}.
     */
    suspend fun saveUserProfile(profile: UserProfile) {
        try {
            val data = hashMapOf(
                "uid" to profile.uid,
                "displayName" to profile.displayName,
                "email" to profile.email,
                "photoUrl" to (profile.photoUrl ?: ""),
                "memberSince" to profile.memberSince,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users")
                .document(profile.uid)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Saved profile for user ${profile.uid}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save user profile: ${e.message}")
        }
    }

    /**
     * Retrieves the user profile from Firestore under users/{userId}.
     */
    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            if (!doc.exists()) return null
            UserProfile(
                uid = doc.getString("uid") ?: userId,
                displayName = doc.getString("displayName") ?: "",
                email = doc.getString("email") ?: "",
                photoUrl = doc.getString("photoUrl"),
                memberSince = doc.getLong("memberSince") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get user profile: ${e.message}")
            null
        }
    }

    /**
     * Saves an appliance registered by the user under users/{userId}/appliances/{applianceId}.
     */
    suspend fun saveAppliance(userId: String, appliance: UserAppliance) {
        try {
            val data = hashMapOf(
                "id" to appliance.id,
                "name" to appliance.name,
                "brand" to (appliance.brand ?: ""),
                "model" to (appliance.model ?: ""),
                "category" to (appliance.category ?: ""),
                "manualCount" to appliance.manualCount,
                "subtitle" to (appliance.subtitle ?: ""),
                "createdAt" to appliance.createdAt
            )
            firestore.collection("users")
                .document(userId)
                .collection("appliances")
                .document(appliance.id)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Saved appliance ${appliance.name} for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save appliance: ${e.message}")
        }
    }

    /**
     * Fetches all registered appliances for the user under users/{userId}/appliances.
     */
    suspend fun getAppliances(userId: String): List<UserAppliance> {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("appliances")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                UserAppliance(
                    id = doc.getString("id") ?: doc.id,
                    name = doc.getString("name") ?: "Appliance",
                    brand = doc.getString("brand")?.takeIf { it.isNotBlank() },
                    model = doc.getString("model")?.takeIf { it.isNotBlank() },
                    category = doc.getString("category")?.takeIf { it.isNotBlank() },
                    manualCount = doc.getLong("manualCount")?.toInt() ?: 1,
                    subtitle = doc.getString("subtitle") ?: "Registered appliance",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get appliances: ${e.message}")
            emptyList()
        }
    }

    /**
     * Deletes an appliance from users/{userId}/appliances/{applianceId}.
     */
    suspend fun deleteAppliance(userId: String, applianceId: String) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("appliances")
                .document(applianceId)
                .delete()
                .await()
            Log.d(TAG, "Deleted appliance $applianceId for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete appliance: ${e.message}")
        }
    }

    /**
     * Creates users/{uid} ONLY if it doesn't exist, so a returning user never gets
     * their photo / name / memberSince overwritten on login.
     */
    suspend fun ensureUserProfile(uid: String, email: String, displayName: String): UserProfile {
        return try {
            getUserProfile(uid) ?: UserProfile(
                uid = uid,
                displayName = displayName,
                email = email,
                photoUrl = null,
                memberSince = System.currentTimeMillis()
            ).also { saveUserProfile(it) }
        } catch (e: Exception) {
            Log.w(TAG, "ensureUserProfile failed: ${e.message}")
            UserProfile(uid, displayName, email, null, System.currentTimeMillis())
        }
    }

    /** Partial update of users/{uid}: only the fields you pass are written. */
    suspend fun updateProfileFields(userId: String, displayName: String? = null, photoUrl: String? = null): Boolean {
        return try {
            val data = hashMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
            displayName?.let { data["displayName"] = it }
            photoUrl?.let { data["photoUrl"] = it }
            firestore.collection("users").document(userId).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update profile: ${e.message}")
            false
        }
    }

    /** Uploads the picked image to Storage (users/{uid}/profile.jpg) and returns its download URL, or null. */
    suspend fun uploadProfilePhoto(userId: String, uri: Uri): String? {
        return try {
            val ref = FirebaseStorage.getInstance().reference.child("users/$userId/profile.jpg")
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload profile photo: ${e.message}")
            null
        }
    }

    /**
     * All guides related to one appliance. Looks in users/{uid}/manuals and /bookmarks and matches by
     * stored applianceId (new saves) or by fuzzy appliance name / model (existing data).
     */
    suspend fun getManualsForAppliance(
        userId: String,
        applianceId: String,
        name: String,
        brand: String?,
        model: String?
    ): List<ManualGuide> {
        return try {
            val docs = listOf("manuals", "bookmarks").flatMap { col ->
                firestore.collection("users").document(userId).collection(col).get().await().documents
            }
            docs.filter { doc ->
                val storedId = doc.getString("applianceId")
                (!storedId.isNullOrBlank() && storedId == applianceId) ||
                        sameAppliance(doc.getString("applianceName").orEmpty(), name, brand, model)
            }
                .sortedByDescending { it.getLong("timestamp") ?: 0L }
                .map { it.toManualGuide() }
                .distinctBy { g -> g.manualId?.takeIf { it.isNotBlank() } ?: (g.applianceName + g.issueTitle) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch manuals for appliance: ${e.message}")
            emptyList()
        }
    }

    private fun norm(s: String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun sameAppliance(manualName: String, name: String, brand: String?, model: String?): Boolean {
        val m = norm(manualName)
        if (m.isEmpty()) return false
        val n = norm(name)
        val full = norm("$brand $name")
        val mod = norm(model)
        return m == n || m == full ||
                (minOf(m.length, n.length) >= 4 && (m.contains(n) || n.contains(m))) ||
                (mod.length >= 4 && m.contains(mod))
    }

    private fun DocumentSnapshot.toManualGuide(): ManualGuide {
        val stepsList = (get("steps") as? List<*>)?.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            ManualStep(
                number = (map["number"] as? Number)?.toInt() ?: 1,
                title = map["title"] as? String ?: "",
                description = map["description"] as? String ?: ""
            )
        } ?: emptyList()
        return ManualGuide(
            applianceName = getString("applianceName") ?: "Appliance",
            issueTitle = getString("issueTitle") ?: "Repair Guide",
            overview = getString("overview") ?: "",
            steps = stepsList,
            imageUrl = getString("imageUrl"),
            manualId = getString("manualId")
        )
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
