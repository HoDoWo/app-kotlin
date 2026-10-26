package com.hodowo.viewmodel

import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.hodowo.data.AuthManager
import com.hodowo.data.FirebaseRepository
import com.hodowo.data.ProfilePhotoStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the profile, appliance and bookmark functions of [ChatViewModel]
 * used by the Profile and Appliance manuals screens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelProfileTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = BookmarkAiService()
    private val authManager: AuthManager = mockk(relaxed = true)
    private val repository: FirebaseRepository = mockk(relaxed = true)
    private val photoStore: ProfilePhotoStore = mockk(relaxed = true)
    private val signedInUser = MutableStateFlow<FirebaseUser?>(null)

    private val washer = UserAppliance(id = "a1", name = "Washer", brand = "Bosch", model = "X1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authManager.currentUserId } returns "u1"
        every { authManager.currentUser } returns signedInUser
        coEvery { repository.getBookmarks(any()) } returns emptyList()
        coEvery { repository.getAppliances(any()) } returns emptyList()
        coEvery { repository.getUserProfile(any()) } returns null
        coEvery { repository.updateProfileFields(any(), any(), any()) } returns true
        coEvery { repository.saveAppliance(any(), any()) } returns true
        coEvery { repository.getManualsForAppliance(any(), any(), any(), any(), any()) } returns emptyList()
        every { photoStore.currentUrl(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun signIn(uid: String = "u1") {
        val user = mockk<FirebaseUser>(relaxed = true)
        every { user.uid } returns uid
        every { user.email } returns "anna@mail.com"
        signedInUser.value = user
    }

    private fun TestScope.createViewModel(
        auth: AuthManager? = authManager,
        store: ProfilePhotoStore? = photoStore,
        history: ChatHistoryStore? = null
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            aiService = service,
            firebaseRepository = repository,
            authManager = auth,
            chatHistory = history,
            profilePhotoStore = store
        )
        advanceUntilIdle()
        return viewModel
    }

    /** Collects the snackbar messages emitted by [viewModel]. */
    private fun TestScope.messagesOf(viewModel: ChatViewModel): MutableList<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.userMessages.toList(messages)
        }
        return messages
    }

    // ---------- refreshAppliances / refreshUserProfile ----------

    @Test
    fun initLoadsAppliancesAndProfile() = runTest(dispatcher) {
        signIn()
        val profile = UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 1L)
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        coEvery { repository.getUserProfile("u1") } returns profile
        every { photoStore.currentUrl("u1") } returns "file://local.jpg"

        val viewModel = createViewModel()

        assertEquals(listOf(washer), viewModel.uiState.value.userAppliances)
        // The cloud photo wins over the photo kept on the phone.
        assertEquals(profile, viewModel.uiState.value.userProfile)
    }

    @Test
    fun profileWithoutPhotoUsesThePhotoKeptOnThePhone() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getUserProfile("u1") } returns UserProfile("u1", "Anna", "anna@mail.com", "", 1L)
        every { photoStore.currentUrl("u1") } returns "file://local.jpg"

        val viewModel = createViewModel()

        assertEquals("file://local.jpg", viewModel.uiState.value.userProfile?.photoUrl)
        assertEquals("Anna", viewModel.uiState.value.userProfile?.displayName)
    }

    @Test
    fun missingProfileWithLocalPhotoCreatesProfileWithThatPhoto() = runTest(dispatcher) {
        signIn()
        every { photoStore.currentUrl("u1") } returns "file://local.jpg"

        val viewModel = createViewModel()

        val profile = viewModel.uiState.value.userProfile
        assertEquals("u1", profile?.uid)
        assertEquals("file://local.jpg", profile?.photoUrl)
    }

    @Test
    fun missingProfileWithoutLocalPhotoLeavesProfileEmpty() = runTest(dispatcher) {
        signIn()
        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.userProfile)
    }

    @Test
    fun guestHasNoProfileEvenIfAPhotoIsOnThePhone() = runTest(dispatcher) {
        every { photoStore.currentUrl(any()) } returns "file://local.jpg"

        val viewModel = createViewModel()

        assertNull(viewModel.uiState.value.userProfile)
        coVerify(exactly = 0) { repository.getUserProfile(any()) }
        coVerify(exactly = 0) { repository.getAppliances(any()) }
    }

    @Test
    fun withoutAuthManagerNothingIsLoaded() = runTest(dispatcher) {
        val viewModel = createViewModel(auth = null)

        assertTrue(viewModel.uiState.value.userAppliances.isEmpty())
        assertNull(viewModel.uiState.value.userProfile)
        coVerify(exactly = 0) { repository.getAppliances(any()) }
        coVerify(exactly = 0) { repository.getUserProfile(any()) }
    }

    // ---------- addAppliance / deleteAppliance ----------

    @Test
    fun addApplianceIgnoresBlankName() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertFalse(viewModel.addAppliance("   "))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.userAppliances.isEmpty())
        coVerify(exactly = 0) { repository.saveAppliance(any(), any()) }
    }

    @Test
    fun addApplianceShowsItRightAwayAndSavesIt() = runTest(dispatcher) {
        signIn()
        val viewModel = createViewModel()

        assertTrue(
            viewModel.addAppliance("  Washer ", brand = " Bosch ", model = " ", category = "Laundry", room = " Kitchen ")
        )

        val added = viewModel.uiState.value.userAppliances.single()
        assertEquals("Washer", added.name)
        assertEquals("Bosch", added.brand)
        assertNull(added.model)
        assertEquals("Laundry", added.category)
        assertEquals("Kitchen • Registered appliance", added.subtitle)
        assertEquals(1, added.manualCount)

        coEvery { repository.getAppliances("u1") } returns listOf(added)
        advanceUntilIdle()

        coVerify { repository.saveAppliance("u1", added) }
        assertEquals(listOf(added), viewModel.uiState.value.userAppliances)
    }

    @Test
    fun addApplianceKeepsGivenSubtitle() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.addAppliance("Oven", subtitle = " Built-in ")

        assertEquals("Built-in", viewModel.uiState.value.userAppliances.single().subtitle)
    }

    @Test
    fun addApplianceWithoutRoomUsesDefaultSubtitle() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.addAppliance("Oven")

        assertEquals("Registered appliance", viewModel.uiState.value.userAppliances.single().subtitle)
    }

    @Test
    fun guestApplianceIsShownButNotSaved() = runTest(dispatcher) {
        val viewModel = createViewModel()

        assertFalse(viewModel.addAppliance("Oven"))
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.saveAppliance(any(), any()) }
        assertEquals("Oven", viewModel.uiState.value.userAppliances.single().name)
    }

    @Test
    fun failedSaveKeepsTheApplianceAndWarns() = runTest(dispatcher) {
        signIn()
        coEvery { repository.saveAppliance(any(), any()) } returns false
        val viewModel = createViewModel()
        val messages = messagesOf(viewModel)

        assertTrue(viewModel.addAppliance("Oven"))
        advanceUntilIdle()

        assertEquals("Oven", viewModel.uiState.value.userAppliances.single().name)
        assertEquals(listOf("Couldn't save the appliance. Check your connection."), messages)
        // Not reloaded from Firestore, which would hide the new appliance.
        coVerify(exactly = 1) { repository.getAppliances("u1") }
    }

    @Test
    fun deleteApplianceRemovesItAndDeletesIt() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        val viewModel = createViewModel()

        coEvery { repository.getAppliances("u1") } returns emptyList()
        viewModel.deleteAppliance("a1")

        assertTrue(viewModel.uiState.value.userAppliances.isEmpty())
        advanceUntilIdle()
        coVerify { repository.deleteAppliance("u1", "a1") }
    }

    @Test
    fun guestDeleteOnlyRemovesTheApplianceFromTheScreen() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.addAppliance("Oven")
        val oven = viewModel.uiState.value.userAppliances.single()

        viewModel.deleteAppliance(oven.id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.userAppliances.isEmpty())
        coVerify(exactly = 0) { repository.deleteAppliance(any(), any()) }
    }

    // ---------- sign in / sign out ----------

    @Test
    fun signOutShowsTheGuestAgain() = runTest(dispatcher) {
        signIn()
        val bookmark = ManualGuide("Washer", "E18", "Clean filter", manualId = "m1")
        coEvery { repository.getBookmarks("u1") } returns listOf(bookmark)
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        coEvery { repository.getUserProfile("u1") } returns UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 1L)
        val history = UserChatStore(mapOf("u1" to listOf(savedChat("chat-1"))))
        val viewModel = createViewModel(history = history)
        viewModel.openApplianceManuals("a1", "Washer", null, null, isRegistered = true)
        viewModel.selectApplianceForChat(Appliance("Washer", "Bosch", "X1", 0.9f))

        assertEquals("http://photo", viewModel.uiState.value.userProfile?.photoUrl)
        assertEquals(listOf("chat-1"), viewModel.uiState.value.chatSessions.map { it.id })

        // Sign out: Firebase has no user any more.
        every { authManager.currentUserId } returns "anonymous"
        signedInUser.value = null
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.userProfile)
        assertTrue(state.userAppliances.isEmpty())
        assertTrue(state.bookmarkedGuides.isEmpty())
        assertTrue(state.chatSessions.isEmpty())
        assertTrue(state.messages.isEmpty())
        assertNull(state.appliance)
        assertNull(viewModel.applianceManuals.value)
    }

    @Test
    fun signingInAsAnotherUserLoadsTheirData() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getUserProfile("u1") } returns UserProfile("u1", "Anna", "anna@mail.com", "http://anna", 1L)
        coEvery { repository.getUserProfile("u2") } returns UserProfile("u2", "Marco", "marco@mail.com", null, 2L)
        coEvery { repository.getAppliances("u2") } returns listOf(washer)
        val history = UserChatStore(
            mapOf("u1" to listOf(savedChat("chat-anna")), "u2" to listOf(savedChat("chat-marco")))
        )
        every { authManager.currentUserId } returns "u1"
        val viewModel = createViewModel(history = history)

        every { authManager.currentUserId } returns "u2"
        signIn("u2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Marco", state.userProfile?.displayName)
        // Marco has no photo: Anna's photo must not stay on screen.
        assertNull(state.userProfile?.photoUrl)
        assertEquals(listOf(washer), state.userAppliances)
        assertEquals(listOf("chat-marco"), state.chatSessions.map { it.id })
    }

    @Test
    fun openingAChatFromTheHistoryKeepsProfileAndAppliances() = runTest(dispatcher) {
        signIn()
        val profile = UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 1L)
        coEvery { repository.getUserProfile("u1") } returns profile
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        val history = UserChatStore(mapOf("u1" to listOf(savedChat("chat-1"))))
        val viewModel = createViewModel(history = history)

        viewModel.openChat("chat-1")

        val state = viewModel.uiState.value
        assertEquals("chat-1", state.currentChatId)
        assertEquals(profile, state.userProfile)
        assertEquals(listOf(washer), state.userAppliances)
        assertEquals("Washer", state.appliance?.name)
    }

    @Test
    fun startingANewChatKeepsProfileAndAppliances() = runTest(dispatcher) {
        signIn()
        val profile = UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 1L)
        coEvery { repository.getUserProfile("u1") } returns profile
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        val viewModel = createViewModel()

        viewModel.startNewChat()

        assertEquals(profile, viewModel.uiState.value.userProfile)
        assertEquals(listOf(washer), viewModel.uiState.value.userAppliances)
    }

    @Test
    fun sameUserEmittedAgainDoesNotResetTheScreen() = runTest(dispatcher) {
        signIn()
        val viewModel = createViewModel()
        viewModel.selectApplianceForChat(Appliance("Washer", "Bosch", "X1", 0.9f))

        signIn() // New FirebaseUser object, same uid.
        advanceUntilIdle()

        assertEquals("Washer", viewModel.uiState.value.appliance?.name)
    }

    // ---------- updateUserDisplayName ----------

    @Test
    fun updateUserDisplayNameIgnoresBlankName() = runTest(dispatcher) {
        signIn()
        val viewModel = createViewModel()

        viewModel.updateUserDisplayName("  ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateProfileFields(any(), any(), any()) }
    }

    @Test
    fun updateUserDisplayNameNeedsASignedInUser() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.updateUserDisplayName("Anna")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userProfile)
        coVerify(exactly = 0) { repository.updateProfileFields(any(), any(), any()) }
    }

    @Test
    fun updateUserDisplayNameKeepsPhotoAndSavesName() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getUserProfile("u1") } returns UserProfile("u1", "Old", "anna@mail.com", "http://photo", 7L)
        val viewModel = createViewModel()
        val messages = messagesOf(viewModel)

        viewModel.updateUserDisplayName("  Anna ")
        advanceUntilIdle()

        assertEquals(
            UserProfile("u1", "Anna", "anna@mail.com", "http://photo", 7L),
            viewModel.uiState.value.userProfile
        )
        coVerify { authManager.updateProfile("Anna") }
        coVerify { repository.updateProfileFields("u1", displayName = "Anna", photoUrl = null) }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun updateUserDisplayNameCreatesProfileAndReportsSaveFailure() = runTest(dispatcher) {
        signIn()
        coEvery { authManager.updateProfile(any()) } throws IllegalStateException("offline")
        coEvery { repository.updateProfileFields(any(), any(), any()) } returns false
        val viewModel = createViewModel()
        val messages = messagesOf(viewModel)

        viewModel.updateUserDisplayName("Anna")
        advanceUntilIdle()

        val profile = viewModel.uiState.value.userProfile
        assertEquals("u1", profile?.uid)
        assertEquals("Anna", profile?.displayName)
        assertEquals("anna@mail.com", profile?.email)
        assertEquals(listOf("Couldn't save your name. Check your connection."), messages)
    }

    // ---------- updateProfilePhoto ----------

    @Test
    fun updateProfilePhotoNeedsASignedInUser() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.updateProfilePhoto(mockk<Uri>())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.uploadProfilePhoto(any(), any()) }
    }

    @Test
    fun updateProfilePhotoUsesTheCloudUrl() = runTest(dispatcher) {
        signIn()
        val uri = mockk<Uri>()
        coEvery { repository.uploadProfilePhoto("u1", uri) } returns "https://cloud/photo.jpg"
        coEvery { repository.getUserProfile("u1") } returns UserProfile("u1", "Anna", "anna@mail.com", null, 7L)
        val viewModel = createViewModel()
        val messages = messagesOf(viewModel)

        viewModel.updateProfilePhoto(uri)
        advanceUntilIdle()

        assertEquals("https://cloud/photo.jpg", viewModel.uiState.value.userProfile?.photoUrl)
        assertEquals("Anna", viewModel.uiState.value.userProfile?.displayName)
        coVerify { repository.updateProfileFields("u1", displayName = null, photoUrl = "https://cloud/photo.jpg") }
        assertEquals(listOf("Profile photo updated"), messages)
        assertFalse(viewModel.isUploadingPhoto.value)
    }

    @Test
    fun updateProfilePhotoKeepsThePhotoOnThePhoneWhenUploadFails() = runTest(dispatcher) {
        signIn()
        val uri = mockk<Uri>()
        coEvery { repository.uploadProfilePhoto("u1", uri) } returns null
        every { photoStore.save("u1", uri) } returns "file://local.jpg"
        val viewModel = createViewModel()
        val messages = messagesOf(viewModel)

        viewModel.updateProfilePhoto(uri)
        advanceUntilIdle()

        assertEquals("file://local.jpg", viewModel.uiState.value.userProfile?.photoUrl)
        assertEquals("u1", viewModel.uiState.value.userProfile?.uid)
        assertEquals(listOf("Profile photo saved on this device"), messages)
        assertFalse(viewModel.isUploadingPhoto.value)
    }

    @Test
    fun updateProfilePhotoReportsFailureWhenNothingCanBeSaved() = runTest(dispatcher) {
        signIn()
        val uri = mockk<Uri>()
        coEvery { repository.uploadProfilePhoto("u1", uri) } returns null
        val viewModel = createViewModel(store = null)
        val messages = messagesOf(viewModel)

        viewModel.updateProfilePhoto(uri)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userProfile)
        assertEquals(listOf("Couldn't upload the photo. Try again."), messages)
        assertFalse(viewModel.isUploadingPhoto.value)
    }

    // ---------- openApplianceManuals / updateAppliance ----------

    @Test
    fun openApplianceManualsLoadsTheGuides() = runTest(dispatcher) {
        signIn()
        val guide = ManualGuide("Washer", "E18", "Clean the filter", manualId = "m1")
        coEvery { repository.getManualsForAppliance("u1", "a1", "Washer", "Bosch", "X1") } returns listOf(guide)
        val viewModel = createViewModel()

        viewModel.openApplianceManuals("a1", "Washer", "Bosch", "X1", isRegistered = true)

        val loading = viewModel.applianceManuals.value!!
        assertTrue(loading.loading)
        assertTrue(loading.isRegistered)

        advanceUntilIdle()

        val loaded = viewModel.applianceManuals.value!!
        assertFalse(loaded.loading)
        assertEquals(listOf(guide), loaded.manuals)
    }

    @Test
    fun openApplianceManualsWithoutUserShowsNoGuides() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.openApplianceManuals("a1", "Washer", null, null, isRegistered = false)
        advanceUntilIdle()

        val state = viewModel.applianceManuals.value!!
        assertFalse(state.loading)
        assertTrue(state.manuals.isEmpty())
        coVerify(exactly = 0) { repository.getManualsForAppliance(any(), any(), any(), any(), any()) }
    }

    @Test
    fun openingAnotherApplianceIgnoresTheOldResult() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getManualsForAppliance("u1", "a1", any(), any(), any()) } returns
                listOf(ManualGuide("Washer", "E18", "Old"))
        val viewModel = createViewModel()

        viewModel.openApplianceManuals("a1", "Washer", null, null, isRegistered = true)
        viewModel.openApplianceManuals("a2", "Oven", null, null, isRegistered = false)
        advanceUntilIdle()

        val state = viewModel.applianceManuals.value!!
        assertEquals("a2", state.id)
        assertTrue(state.manuals.isEmpty())
    }

    @Test
    fun updateApplianceSavesChangesAndReloadsTheScreen() = runTest(dispatcher) {
        signIn()
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        val viewModel = createViewModel()
        val updated = washer.copy(name = "Big washer", brand = "Miele", model = null)
        coEvery { repository.getAppliances("u1") } returns listOf(updated)

        viewModel.updateAppliance("a1", "Big washer", "Miele", " ")
        advanceUntilIdle()

        coVerify { repository.saveAppliance("u1", updated) }
        assertEquals(listOf(updated), viewModel.uiState.value.userAppliances)
        val screen = viewModel.applianceManuals.value!!
        assertEquals("a1", screen.id)
        assertEquals("Big washer", screen.name)
        assertEquals("Miele", screen.brand)
        assertNull(screen.model)
    }

    @Test
    fun updateApplianceIgnoresUnknownApplianceOrMissingUser() = runTest(dispatcher) {
        coEvery { repository.getAppliances("u1") } returns listOf(washer)
        val viewModel = createViewModel()

        // No signed-in user yet.
        viewModel.updateAppliance("a1", "Big washer", "", "")
        signIn()
        // Unknown appliance.
        viewModel.updateAppliance("missing", "Big washer", "", "")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.saveAppliance(any(), any()) }
        assertNull(viewModel.applianceManuals.value)
    }

    // ---------- removeBookmark / selectApplianceForChat ----------

    @Test
    fun removeBookmarkByManualIdUpdatesListAndBackend() = runTest(dispatcher) {
        val kept = ManualGuide("Oven", "F1", "Reset", manualId = "m2")
        val removed = ManualGuide("Washer", "E18", "Clean filter", manualId = "m1")
        coEvery { repository.getBookmarks("u1") } returns listOf(removed, kept)
        val viewModel = createViewModel()

        coEvery { repository.getBookmarks("u1") } returns listOf(kept)
        viewModel.removeBookmark(removed)

        assertEquals(listOf(kept), viewModel.uiState.value.bookmarkedGuides)
        advanceUntilIdle()
        coVerify { repository.removeBookmarkByManualId("u1", "m1") }
        assertEquals(listOf("m1" to false), service.bookmarkCalls)
        assertEquals(listOf(kept), viewModel.uiState.value.bookmarkedGuides)
    }

    @Test
    fun removeBookmarkIgnoresBackendErrors() = runTest(dispatcher) {
        service.bookmarkError = IllegalStateException("offline")
        val removed = ManualGuide("Washer", "E18", "Clean filter", manualId = "m1")
        coEvery { repository.getBookmarks("u1") } returns listOf(removed)
        val viewModel = createViewModel()
        coEvery { repository.getBookmarks("u1") } returns emptyList()

        viewModel.removeBookmark(removed)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.bookmarkedGuides.isEmpty())
    }

    @Test
    fun removeBookmarkWithoutManualIdMatchesByTitle() = runTest(dispatcher) {
        val removed = ManualGuide("Washer", "E18", "Clean filter")
        val kept = ManualGuide("Washer", "E20", "Check pump")
        coEvery { repository.getBookmarks("u1") } returns listOf(removed, kept)
        val viewModel = createViewModel()
        coEvery { repository.getBookmarks("u1") } returns listOf(kept)

        viewModel.removeBookmark(removed)
        assertEquals(listOf(kept), viewModel.uiState.value.bookmarkedGuides)
        advanceUntilIdle()

        assertEquals(listOf(kept), viewModel.uiState.value.bookmarkedGuides)
        coVerify(exactly = 0) { repository.removeBookmarkByManualId(any(), any()) }
        assertTrue(service.bookmarkCalls.isEmpty())
    }

    @Test
    fun selectApplianceForChatSetsTheChatAppliance() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val appliance = Appliance("Washer", "Bosch", "X1", 0.9f)

        viewModel.selectApplianceForChat(appliance)

        assertEquals(appliance, viewModel.uiState.value.appliance)
    }

    private fun savedChat(id: String) = SavedChat(
        id = id,
        title = "Question $id",
        applianceName = "Washer",
        lastUpdated = 1L,
        messages = listOf(Message(id = 1L, text = "Question $id", isUser = true))
    )

    /** Chat history kept per user, like the file store on the phone. */
    private class UserChatStore(initial: Map<String, List<SavedChat>>) : ChatHistoryStore {
        private val chats = initial.toMutableMap()

        override suspend fun load(userId: String): List<SavedChat> = chats[userId].orEmpty()

        override suspend fun save(userId: String, chat: SavedChat) {
            chats[userId] = chats[userId].orEmpty().filterNot { it.id == chat.id } + chat
        }

        override suspend fun delete(userId: String, chatId: String) {
            chats[userId] = chats[userId].orEmpty().filterNot { it.id == chatId }
        }
    }

    /** AI service that only records bookmark changes. */
    private class BookmarkAiService : HoDoWoAiService {
        val bookmarkCalls = mutableListOf<Pair<String, Boolean>>()
        var bookmarkError: Exception? = null

        override suspend fun identifyAppliance(uri: Uri): Appliance = Appliance("Washer", "Bosch", "X1", 0.9f)

        override suspend fun askQuestion(question: String, appliance: Appliance?): String = "answer"

        override suspend fun locatePanel(uri: Uri): PanelBox? = null

        override suspend fun analyzePanel(uri: Uri, appliance: Appliance?): List<PanelControl> = emptyList()

        override suspend fun sendFeedback(report: FeedbackReport) = Unit

        override suspend fun setBookmark(manualId: String, isBookmarked: Boolean): Boolean {
            bookmarkError?.let { throw it }
            bookmarkCalls += manualId to isBookmarked
            return isBookmarked
        }
    }
}
