package com.hodowo.viewmodel

import com.hodowo.data.AppNotification
import com.hodowo.data.InMemoryNotificationSeenStore
import com.hodowo.data.NotificationTarget
import com.hodowo.data.NotificationsService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<NotificationsService>()
    private val store = InMemoryNotificationSeenStore()

    private fun notification(id: String, unread: Boolean = true, kind: String = "message") = AppNotification(
        id = id, kind = kind, title = "Anna", body = "Hello", imageUrl = null, createdAt = 1L,
        unread = unread, target = NotificationTarget.Conversation("c1")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theBadgeCountsUnreadNotificationsNotSeenYet() = runTest(dispatcher) {
        coEvery { service.notifications() } returns listOf(
            notification("m1"), notification("o1", kind = "offer"), notification("s1", unread = false, kind = "sponsored")
        )
        val vm = NotificationsViewModel(service, store)

        vm.load()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.items.size)
        assertEquals(2, vm.uiState.value.badge)
        assertTrue(vm.uiState.value.isNew(vm.uiState.value.items[0]))
        assertFalse(vm.uiState.value.isNew(vm.uiState.value.items[2]))
    }

    @Test
    fun closingTheListMarksEverythingAsSeen() = runTest(dispatcher) {
        coEvery { service.notifications() } returns listOf(notification("m1"))
        val vm = NotificationsViewModel(service, store)

        vm.open()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isOpen)
        vm.close()

        assertFalse(vm.uiState.value.isOpen)
        assertEquals(0, vm.uiState.value.badge)
        assertEquals(setOf("m1"), store.seen())
    }

    @Test
    fun aNewMessageCountsAgainAfterTheOldOnesWereSeen() = runTest(dispatcher) {
        store.save(setOf("m1"))
        coEvery { service.notifications() } returns listOf(notification("m1"), notification("m2"))
        val vm = NotificationsViewModel(service, store)

        vm.load()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.badge)
    }

    @Test
    fun whileTheListIsOpenNewNotificationsAreSeenAtOnce() = runTest(dispatcher) {
        coEvery { service.notifications() } returns listOf(notification("m1"))
        val vm = NotificationsViewModel(service, store)
        vm.open()
        advanceUntilIdle()

        coEvery { service.notifications() } returns listOf(notification("m1"), notification("m2"))
        vm.load()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.badge)
        assertEquals(setOf("m1", "m2"), store.seen())
    }

    @Test
    fun anErrorIsShownAndKeepsWhatWasThere() = runTest(dispatcher) {
        coEvery { service.notifications() } returns listOf(notification("m1"))
        val vm = NotificationsViewModel(service, store)
        vm.load()
        advanceUntilIdle()

        coEvery { service.notifications() } throws IOException("Notifications are unavailable (503)")
        vm.load()
        advanceUntilIdle()

        assertEquals("Notifications are unavailable (503)", vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.items.size)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun closingWithNothingNewKeepsTheStore() {
        val vm = NotificationsViewModel(service, store)

        vm.close()

        assertNull(vm.uiState.value.error)
        assertEquals(emptySet<String>(), store.seen())
    }
}
