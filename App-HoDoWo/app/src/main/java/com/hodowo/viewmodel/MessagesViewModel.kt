package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hodowo.data.CartStore
import com.hodowo.data.Conversation
import com.hodowo.data.MessagesService
import com.hodowo.data.parseEuroAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Everything the inbox and the open conversation show. */
data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    /** Unread messages in all conversations, for the badge. */
    val unread: Int = 0,
    val isLoading: Boolean = false,
    /** Loading the inbox failed: shown with a retry button. */
    val error: String? = null,
    /** The conversation on screen, or null for the inbox. */
    val open: Conversation? = null,
    val isOpening: Boolean = false,
    /** A message, offer or answer is being sent: the buttons wait. */
    val isSending: Boolean = false,
    /** Show the offer dialog as soon as the conversation is open ("Make an offer" on a listing). */
    val askOffer: Boolean = false,
    /** One-off feedback ("an offer must be at least €50.00"); cleared by [MessagesViewModel.consumeMessage]. */
    val message: String? = null,
    /** The accepted offer is in the cart: the screen shows the cart; cleared by [MessagesViewModel.onCartOpened]. */
    val openCart: Boolean = false
)

/**
 * Messages between buyers and sellers of second-hand listings, with Vinted-style
 * offers. Every rule (who may offer, the lowest offer, expiry) is the backend's:
 * the app shows what it answers and its error messages.
 */
class MessagesViewModel(
    private val service: MessagesService,
    private val cartStore: CartStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    fun loadInbox() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val inbox = service.inbox()
                _uiState.update { it.copy(isLoading = false, conversations = inbox.conversations, unread = inbox.unread) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Could not load your messages") }
            }
        }
    }

    fun openConversation(conversationId: String) = load(askOffer = false) { service.open(conversationId) }

    /** "Message the seller" / "Make an offer" on a listing: opens its conversation. */
    fun contactSeller(listingId: String, makeOffer: Boolean) = load(askOffer = makeOffer) { service.start(listingId) }

    /** Reloads the open conversation, to see new messages (called every few seconds while it is shown). */
    fun refreshOpen() {
        val id = _uiState.value.open?.id ?: return
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            try {
                show(service.open(id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Polling: the next refresh tries again.
            }
        }
    }

    fun close() {
        _uiState.update { it.copy(open = null, askOffer = false) }
        loadInbox()
    }

    fun send(text: String) {
        val clean = text.trim()
        val id = _uiState.value.open?.id ?: return
        if (clean.isEmpty()) return
        act { service.send(id, clean) }
    }

    /** [amountText] as typed: "85", "85,50"... Returns false when it is not an amount. */
    fun makeOffer(amountText: String): Boolean {
        val id = _uiState.value.open?.id ?: return false
        val cents = parseEuroAmount(amountText)
        if (cents == null || cents <= 0) {
            _uiState.update { it.copy(message = "Write an amount, for example 85 or 85,50") }
            return false
        }
        act { service.offer(id, cents) }
        return true
    }

    fun acceptOffer() = answer(accept = true)

    fun declineOffer() = answer(accept = false)

    /** Puts the listing of the accepted offer in the cart (at the offered price) and opens the cart. */
    fun payOffer() {
        val conversation = _uiState.value.open ?: return
        val store = cartStore ?: return
        if (conversation.offer?.canPay != true) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val inCart = store.cart.value.items.any { it.productType == USED && it.productId == conversation.listing.id }
            val message = try {
                if (inCart) store.refresh() else store.add(USED, conversation.listing.id)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                e.message ?: "Could not add it to the cart"
            }
            _uiState.update { it.copy(isSending = false, message = message, openCart = message == null) }
        }
    }

    fun onOfferDialogShown() = _uiState.update { it.copy(askOffer = false) }

    fun onCartOpened() = _uiState.update { it.copy(openCart = false) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun answer(accept: Boolean) {
        val id = _uiState.value.open?.id ?: return
        act { service.answerOffer(id, accept) }
    }

    private fun load(askOffer: Boolean, request: suspend () -> Conversation) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true, message = null) }
            try {
                val conversation = request()
                show(conversation)
                _uiState.update { it.copy(isOpening = false, askOffer = askOffer && conversation.canOffer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isOpening = false, message = e.message ?: "Could not open the conversation") }
            }
        }
    }

    private fun act(request: suspend () -> Conversation) {
        if (_uiState.value.isSending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, message = null) }
            try {
                show(request())
                _uiState.update { it.copy(isSending = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _uiState.update { it.copy(isSending = false, message = e.message ?: "Could not send, try again") }
            }
        }
    }

    /** Shows [conversation] and updates its row and the unread total of the inbox. */
    private fun show(conversation: Conversation) {
        _uiState.update { state ->
            val rows = listOf(conversation.copy(messages = emptyList())) +
                state.conversations.filterNot { it.id == conversation.id }
            val sorted = rows.sortedByDescending { it.updatedAt ?: 0L }
            state.copy(open = conversation, conversations = sorted, unread = sorted.sumOf { it.unread })
        }
    }

    private companion object {
        const val USED = "used"
    }
}
