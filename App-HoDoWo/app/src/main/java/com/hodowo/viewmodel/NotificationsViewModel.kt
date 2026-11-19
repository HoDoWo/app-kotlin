package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.AppNotification
import com.hodowo.data.InMemoryNotificationSeenStore
import com.hodowo.data.NotificationSeenStore
import com.hodowo.data.NotificationsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** What the bell and its list show. */
data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Notifications already shown to the user: they no longer count in the badge. */
    val seen: Set<String> = emptySet(),
    /** The list is open. */
    val isOpen: Boolean = false,
    /**
     * While the list is open: the notifications that were new when it was opened (or
     * arrived while it was open). They stay highlighted although the badge is already 0.
     */
    val highlighted: Set<String> = emptySet()
) {
    /** The number on the bell: new messages, offers, price changes and sponsored products not seen yet. */
    val badge: Int get() = items.count { counts(it) && it.id !in seen }

    /** A notification the user has not seen yet, shown highlighted in the list. */
    fun isNew(item: AppNotification): Boolean =
        if (isOpen) item.id in highlighted else counts(item) && item.id !in seen

    /** The ids that are new now: they count in the badge. */
    internal fun newIds(): Set<String> = items.filter { counts(it) && it.id !in seen }.map { it.id }.toSet()

    private companion object {
        /** The sponsored product is not "unread" for the backend, but a new one still counts on the bell. */
        fun counts(item: AppNotification): Boolean = item.unread || item.kind == SPONSORED
        const val SPONSORED = "sponsored"
    }
}

/**
 * The notifications of the bell (messages, offers on the user's listings and
 * on what they want to buy, price changes in the cart, a sponsored product).
 * Opening the list marks everything in it as seen: the badge goes to 0, but what
 * was new stays highlighted until the list is closed.
 */
class NotificationsViewModel(
    private val service: NotificationsService,
    private val seenStore: NotificationSeenStore = InMemoryNotificationSeenStore()
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(seen = seenStore.seen()))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val items = service.notifications()
                _uiState.update { state ->
                    val loaded = state.copy(isLoading = false, items = items)
                    if (state.isOpen) loaded.copy(highlighted = state.highlighted + loaded.newIds()) else loaded
                }
                if (_uiState.value.isOpen) markSeen()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your notifications") }
            }
        }
    }

    fun open() {
        _uiState.update { it.copy(isOpen = true, highlighted = it.newIds()) }
        markSeen()
        load()
    }

    /** Closes the list; what was in it is now seen. */
    fun close() {
        markSeen()
        _uiState.update { it.copy(isOpen = false, highlighted = emptySet()) }
    }

    /**
     * Remembers what is in the list as seen. Only the ids still notified are
     * kept, so the store does not grow forever.
     */
    private fun markSeen() {
        val ids = _uiState.value.items.map { it.id }.toSet()
        if (ids.isEmpty() || ids == _uiState.value.seen) return
        seenStore.save(ids)
        _uiState.update { it.copy(seen = ids) }
    }
}
