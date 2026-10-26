package com.hodowo.data

import android.net.Uri
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.hodowo.viewmodel.UserAppliance
import com.hodowo.viewmodel.UserProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the profile, appliance and appliance-manual functions of [FirebaseRepository].
 */
class FirebaseRepositoryProfileTest {

    private val firestore: FirebaseFirestore = mockk(relaxed = true)
    private val usersCollection: CollectionReference = mockk(relaxed = true)
    private val userDoc: DocumentReference = mockk(relaxed = true)
    private val repository = FirebaseRepository(firestore)

    init {
        every { firestore.collection("users") } returns usersCollection
        every { usersCollection.document(any()) } returns userDoc
    }

    // ---------- helpers ----------

    private fun <T> successTask(result: T): Task<T> {
        val task = mockk<Task<T>>(relaxed = true)
        val listener = slot<OnSuccessListener<T>>()
        every { task.addOnSuccessListener(capture(listener)) } answers {
            listener.captured.onSuccess(result)
            task
        }
        return task
    }

    private fun <T> failureTask(error: Exception = RuntimeException("Firestore error")): Task<T> {
        val task = mockk<Task<T>>(relaxed = true)
        val listener = slot<OnFailureListener>()
        every { task.addOnFailureListener(capture(listener)) } answers {
            listener.captured.onFailure(error)
            task
        }
        return task
    }

    private fun query(vararg docs: DocumentSnapshot): QuerySnapshot {
        val snapshot = mockk<QuerySnapshot>(relaxed = true)
        every { snapshot.documents } returns docs.toList()
        return snapshot
    }

    private fun subCollection(name: String): CollectionReference {
        val collection = mockk<CollectionReference>(relaxed = true)
        every { userDoc.collection(name) } returns collection
        return collection
    }

    private fun guideDoc(
        applianceName: String?,
        applianceId: String? = null,
        manualId: String? = null,
        issueTitle: String? = "Issue",
        timestamp: Long? = null,
        steps: Any? = null
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.getString(any()) } returns null
        every { doc.getString("applianceName") } returns applianceName
        every { doc.getString("applianceId") } returns applianceId
        every { doc.getString("manualId") } returns manualId
        every { doc.getString("issueTitle") } returns issueTitle
        every { doc.getLong("timestamp") } returns timestamp
        every { doc.get("steps") } returns steps
        return doc
    }

    // ---------- removeBookmarkByManualId ----------

    @Test
    fun removeBookmarkByManualIdDeletesEveryMatchingDocument() = runTest {
        val bookmarks = subCollection("bookmarks")
        val filtered = mockk<Query>(relaxed = true)
        val ref1 = mockk<DocumentReference>(relaxed = true)
        val ref2 = mockk<DocumentReference>(relaxed = true)
        val doc1 = mockk<DocumentSnapshot> { every { reference } returns ref1 }
        val doc2 = mockk<DocumentSnapshot> { every { reference } returns ref2 }
        every { bookmarks.whereEqualTo("manualId", "m-1") } returns filtered
        every { filtered.get() } returns successTask(query(doc1, doc2))
        every { ref1.delete() } returns successTask<Void?>(null)
        every { ref2.delete() } returns successTask<Void?>(null)

        repository.removeBookmarkByManualId("user-1", "m-1")

        verify { ref1.delete() }
        verify { ref2.delete() }
    }

    @Test
    fun removeBookmarkByManualIdIgnoresErrors() = runTest {
        val bookmarks = subCollection("bookmarks")
        val filtered = mockk<Query>(relaxed = true)
        every { bookmarks.whereEqualTo("manualId", "m-1") } returns filtered
        every { filtered.get() } returns failureTask()

        repository.removeBookmarkByManualId("user-1", "m-1")
    }

    // ---------- saveUserProfile / getUserProfile ----------

    @Test
    fun saveUserProfileWritesAllFields() = runTest {
        val data = slot<Any>()
        every { userDoc.set(capture(data), any()) } returns successTask<Void?>(null)

        repository.saveUserProfile(UserProfile("u1", "Anna", "anna@mail.com", null, 42L))

        val saved = data.captured as Map<*, *>
        assertEquals("u1", saved["uid"])
        assertEquals("Anna", saved["displayName"])
        assertEquals("anna@mail.com", saved["email"])
        assertEquals("", saved["photoUrl"])
        assertEquals(42L, saved["memberSince"])
        verify { usersCollection.document("u1") }
    }

    @Test
    fun saveUserProfileIgnoresErrors() = runTest {
        every { userDoc.set(any(), any()) } returns failureTask()

        repository.saveUserProfile(UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 42L))
    }

    @Test
    fun getUserProfileReadsStoredFields() = runTest {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.exists() } returns true
        every { doc.getString("uid") } returns "u1"
        every { doc.getString("displayName") } returns "Anna"
        every { doc.getString("email") } returns "anna@mail.com"
        every { doc.getString("photoUrl") } returns "http://photo"
        every { doc.getLong("memberSince") } returns 42L
        every { userDoc.get() } returns successTask(doc)

        val profile = repository.getUserProfile("u1")

        assertEquals(UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 42L), profile)
    }

    @Test
    fun getUserProfileUsesDefaultsForMissingFields() = runTest {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.exists() } returns true
        every { doc.getString(any()) } returns null
        every { doc.getLong(any()) } returns null
        every { userDoc.get() } returns successTask(doc)

        val profile = repository.getUserProfile("u1")!!

        assertEquals("u1", profile.uid)
        assertEquals("", profile.displayName)
        assertEquals("", profile.email)
        assertNull(profile.photoUrl)
        assertTrue(profile.memberSince > 0)
    }

    @Test
    fun getUserProfileReturnsNullWhenDocumentDoesNotExist() = runTest {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.exists() } returns false
        every { userDoc.get() } returns successTask(doc)

        assertNull(repository.getUserProfile("u1"))
    }

    @Test
    fun getUserProfileReturnsNullOnError() = runTest {
        every { userDoc.get() } returns failureTask()

        assertNull(repository.getUserProfile("u1"))
    }

    // ---------- ensureUserProfile ----------

    @Test
    fun ensureUserProfileKeepsExistingProfile() = runTest {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.exists() } returns true
        every { doc.getString("uid") } returns "u1"
        every { doc.getString("displayName") } returns "Old name"
        every { doc.getString("email") } returns "old@mail.com"
        every { doc.getString("photoUrl") } returns "http://photo"
        every { doc.getLong("memberSince") } returns 7L
        every { userDoc.get() } returns successTask(doc)

        val profile = repository.ensureUserProfile("u1", "new@mail.com", "New name")

        assertEquals("Old name", profile.displayName)
        assertEquals(7L, profile.memberSince)
        verify(exactly = 0) { userDoc.set(any(), any()) }
    }

    @Test
    fun ensureUserProfileCreatesMissingProfile() = runTest {
        val doc = mockk<DocumentSnapshot>(relaxed = true)
        every { doc.exists() } returns false
        every { userDoc.get() } returns successTask(doc)
        every { userDoc.set(any(), any()) } returns successTask<Void?>(null)

        val profile = repository.ensureUserProfile("u1", "anna@mail.com", "Anna")

        assertEquals("u1", profile.uid)
        assertEquals("Anna", profile.displayName)
        assertEquals("anna@mail.com", profile.email)
        assertNull(profile.photoUrl)
        verify { userDoc.set(any(), any()) }
    }

    // ---------- updateProfileFields ----------

    @Test
    fun updateProfileFieldsWritesOnlyGivenFields() = runTest {
        val data = slot<Any>()
        every { userDoc.set(capture(data), any()) } returns successTask<Void?>(null)

        assertTrue(repository.updateProfileFields("u1", displayName = "Anna", photoUrl = "http://photo"))

        val saved = data.captured as Map<*, *>
        assertEquals("Anna", saved["displayName"])
        assertEquals("http://photo", saved["photoUrl"])
        assertTrue(saved.containsKey("updatedAt"))
    }

    @Test
    fun updateProfileFieldsSkipsNullFields() = runTest {
        val data = slot<Any>()
        every { userDoc.set(capture(data), any()) } returns successTask<Void?>(null)

        assertTrue(repository.updateProfileFields("u1"))

        val saved = data.captured as Map<*, *>
        assertFalse(saved.containsKey("displayName"))
        assertFalse(saved.containsKey("photoUrl"))
    }

    @Test
    fun updateProfileFieldsReturnsFalseOnError() = runTest {
        every { userDoc.set(any(), any()) } returns failureTask()

        assertFalse(repository.updateProfileFields("u1", displayName = "Anna"))
    }

    // ---------- uploadProfilePhoto ----------

    @Test
    fun uploadProfilePhotoReturnsNullWhenStorageIsUnavailable() = runTest {
        mockkStatic(FirebaseStorage::class)
        try {
            every { FirebaseStorage.getInstance() } throws IllegalStateException("Storage not configured")

            assertNull(repository.uploadProfilePhoto("u1", mockk<Uri>()))
        } finally {
            unmockkStatic(FirebaseStorage::class)
        }
    }

    // ---------- appliances ----------

    @Test
    fun saveApplianceWritesAllFields() = runTest {
        val appliances = subCollection("appliances")
        val applianceDoc = mockk<DocumentReference>(relaxed = true)
        val data = slot<Any>()
        every { appliances.document("a1") } returns applianceDoc
        every { applianceDoc.set(capture(data), any()) } returns successTask<Void?>(null)

        val written = repository.saveAppliance(
            "u1",
            UserAppliance(id = "a1", name = "Washer", brand = "Bosch", model = null, createdAt = 5L)
        )

        assertTrue(written)

        val saved = data.captured as Map<*, *>
        assertEquals("a1", saved["id"])
        assertEquals("Washer", saved["name"])
        assertEquals("Bosch", saved["brand"])
        assertEquals("", saved["model"])
        assertEquals("", saved["category"])
        assertEquals("", saved["subtitle"])
        assertEquals(1, saved["manualCount"])
        assertEquals(5L, saved["createdAt"])
    }

    @Test
    fun saveApplianceReturnsFalseOnError() = runTest {
        val appliances = subCollection("appliances")
        val applianceDoc = mockk<DocumentReference>(relaxed = true)
        every { appliances.document("a1") } returns applianceDoc
        every { applianceDoc.set(any(), any()) } returns failureTask()

        val saved = repository.saveAppliance(
            "u1",
            UserAppliance(id = "a1", name = "Washer", model = "X1", category = "Laundry", subtitle = "Kitchen")
        )

        assertFalse(saved)
    }

    @Test
    fun getAppliancesMapsDocuments() = runTest {
        val full = mockk<DocumentSnapshot>(relaxed = true)
        every { full.getString("id") } returns "a1"
        every { full.getString("name") } returns "Washer"
        every { full.getString("brand") } returns "Bosch"
        every { full.getString("model") } returns "X1"
        every { full.getString("category") } returns "Laundry"
        every { full.getString("subtitle") } returns "Kitchen"
        every { full.getLong("manualCount") } returns 3L
        every { full.getLong("createdAt") } returns 9L

        val empty = mockk<DocumentSnapshot>(relaxed = true)
        every { empty.id } returns "doc-2"
        every { empty.getString(any()) } returns null
        every { empty.getString("brand") } returns " "
        every { empty.getLong(any()) } returns null

        val appliancesCollection = subCollection("appliances")
        every { appliancesCollection.get() } returns successTask(query(full, empty))

        val list = repository.getAppliances("u1")

        assertEquals(
            UserAppliance("a1", "Washer", "Bosch", "X1", "Laundry", 3, "Kitchen", 9L),
            list[0]
        )
        val fallback = list[1]
        assertEquals("doc-2", fallback.id)
        assertEquals("Appliance", fallback.name)
        assertNull(fallback.brand)
        assertNull(fallback.model)
        assertNull(fallback.category)
        assertEquals(1, fallback.manualCount)
        assertEquals("Registered appliance", fallback.subtitle)
    }

    @Test
    fun getAppliancesReturnsEmptyListOnError() = runTest {
        val appliancesCollection = subCollection("appliances")
        every { appliancesCollection.get() } returns failureTask()

        assertTrue(repository.getAppliances("u1").isEmpty())
    }

    @Test
    fun deleteApplianceDeletesTheDocument() = runTest {
        val appliances = subCollection("appliances")
        val applianceDoc = mockk<DocumentReference>(relaxed = true)
        every { appliances.document("a1") } returns applianceDoc
        every { applianceDoc.delete() } returns successTask<Void?>(null)

        repository.deleteAppliance("u1", "a1")

        verify { applianceDoc.delete() }
    }

    @Test
    fun deleteApplianceIgnoresErrors() = runTest {
        val appliances = subCollection("appliances")
        val applianceDoc = mockk<DocumentReference>(relaxed = true)
        every { appliances.document("a1") } returns applianceDoc
        every { applianceDoc.delete() } returns failureTask()

        repository.deleteAppliance("u1", "a1")
    }

    // ---------- getManualsForAppliance ----------

    @Test
    fun getManualsForApplianceMatchesByIdNameAndModel() = runTest {
        val byId = guideDoc(
            applianceName = "Something else",
            applianceId = "a1",
            manualId = "m-id",
            timestamp = 1L,
            steps = listOf(
                mapOf("number" to 2, "title" to "Open", "description" to "Open the door"),
                mapOf<String, Any>(),
                "not a step"
            )
        )
        val byExactName = guideDoc("Washer", manualId = "m-name", timestamp = 5L)
        val byBrandAndName = guideDoc("Bosch Washer", manualId = "m-brand", timestamp = 4L)
        val byContainedName = guideDoc("Big Washer 9000", manualId = "m-contains", timestamp = 3L)
        val byModel = guideDoc("Front loader WAX32", manualId = "m-model", timestamp = 2L)
        val other = guideDoc("Fridge", applianceId = "other", manualId = "m-other")
        val noName = guideDoc(applianceName = null, manualId = "m-none")
        // Same guide stored both as manual and bookmark: listed once.
        val duplicate = guideDoc("Washer", manualId = "m-name", timestamp = 0L)
        // No manual id: de-duplicated by appliance name + title.
        val noId1 = guideDoc("Washer", issueTitle = "E18", timestamp = 0L)
        val noId2 = guideDoc("Washer", manualId = " ", issueTitle = "E18", timestamp = 0L)

        val manualsCollection = subCollection("manuals")
        every { manualsCollection.get() } returns
                successTask(query(byId, byExactName, other, noName, noId1))
        val bookmarksCollection = subCollection("bookmarks")
        every { bookmarksCollection.get() } returns
                successTask(query(byBrandAndName, byContainedName, byModel, duplicate, noId2))

        val guides = repository.getManualsForAppliance("u1", "a1", "Washer", "Bosch", "WAX32")

        assertEquals(
            listOf("m-name", "m-brand", "m-contains", "m-model", "m-id", null),
            guides.map { it.manualId }
        )
        val stepped = guides.first { it.manualId == "m-id" }
        assertEquals(2, stepped.steps.size)
        assertEquals(2, stepped.steps[0].number)
        assertEquals("Open the door", stepped.steps[0].description)
        assertEquals(1, stepped.steps[1].number)
        assertEquals("", stepped.steps[1].title)
    }

    @Test
    fun getManualsForApplianceUsesDefaultsForMissingGuideFields() = runTest {
        val doc = guideDoc(applianceName = null, applianceId = "a1", issueTitle = null)
        val manualsCollection = subCollection("manuals")
        every { manualsCollection.get() } returns successTask(query(doc))
        val bookmarksCollection = subCollection("bookmarks")
        every { bookmarksCollection.get() } returns successTask(query())

        val guide = repository.getManualsForAppliance("u1", "a1", "Oven", null, null).single()

        assertEquals("Appliance", guide.applianceName)
        assertEquals("Repair Guide", guide.issueTitle)
        assertEquals("", guide.overview)
        assertTrue(guide.steps.isEmpty())
        assertNull(guide.imageUrl)
    }

    @Test
    fun getManualsForApplianceIgnoresShortNamesAndModels() = runTest {
        // "Ov" is too short for a partial match and "X1" too short for a model match.
        val doc = guideDoc("Oven X1 deluxe", manualId = "m1")
        val manualsCollection = subCollection("manuals")
        every { manualsCollection.get() } returns successTask(query(doc))
        val bookmarksCollection = subCollection("bookmarks")
        every { bookmarksCollection.get() } returns successTask(query())

        assertTrue(repository.getManualsForAppliance("u1", "a1", "Ov", null, "X1").isEmpty())
    }

    @Test
    fun getManualsForApplianceReturnsEmptyListOnError() = runTest {
        val manualsCollection = subCollection("manuals")
        every { manualsCollection.get() } returns failureTask()

        assertTrue(repository.getManualsForAppliance("u1", "a1", "Washer", null, null).isEmpty())
    }
}