package com.hodowo.viewmodel

import com.hodowo.data.Cart
import com.hodowo.data.CartService
import com.hodowo.data.CartStore
import com.hodowo.data.Conversation
import com.hodowo.data.ConversationListing
import com.hodowo.data.ConversationMessage
import com.hodowo.data.Inbox
import com.hodowo.data.MessagesException
import com.hodowo.data.MessagesService
import com.hodowo.data.Offer
import io.mockk.coEvery
import io.mockk.coVerify
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
class MessagesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val service = mockk<MessagesService>()
    private val cartService = mockk<CartService>()
    private val cartStore = CartStore(cartService)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun conversation(
        id: String = "fridge__b1",
        unread: Int = 0,
        updatedAt: Long = 1,
        offer: Offer? = null,
        canOffer: Boolean = true,
        messages: List<ConversationMessage> = emptyList()
    ) = Conversation(
        id = id, buying = true, otherName = "Marc",
        listing = ConversationListing("fridge", "Old fridge", null, 10000, "active"),
        lastMessage = null, unread = unread, offer = offer, canOffer = canOffer, updatedAt = updatedAt,
        messages = messages
    )

    private fun accepted() = Offer("of1", 8000, true, "accepted", 9, 20, canAnswer = false, canPay = true)

    @Test
    fun theInboxIsLoadedWithTheUnreadTotal() = runTest(dispatcher) {
        coEvery { service.inbox() } returns Inbox(listOf(conversation(unread = 2)), 2)
        val vm = MessagesViewModel(service)

        vm.loadInbox()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.unread)
        assertEquals(1, vm.uiState.value.conversations.size)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun anInboxErrorIsShownWithRetry() = runTest(dispatcher) {
        coEvery { service.inbox() } throws IOException("offline")
        val vm = MessagesViewModel(service)

        vm.loadInbox()
        advanceUntilIdle()

        assertEquals("offline", vm.uiState.value.error)
    }

    @Test
    fun openingAConversationMarksItReadInTheInbox() = runTest(dispatcher) {
        coEvery { service.inbox() } returns Inbox(listOf(conversation(unread = 2), conversation("other", unread = 1)), 3)
        coEvery { service.open("fridge__b1") } returns conversation(unread = 0, updatedAt = 5)
        val vm = MessagesViewModel(service)
        vm.loadInbox()
        advanceUntilIdle()

        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        assertEquals("fridge__b1", vm.uiState.value.open?.id)
        assertEquals(1, vm.uiState.value.unread)
        assertEquals(listOf("fridge__b1", "other"), vm.uiState.value.conversations.map { it.id })
    }

    @Test
    fun makeAnOfferFromAListingOpensTheConversationWithTheOfferDialog() = runTest(dispatcher) {
        coEvery { service.start("fridge", null) } returns conversation()
        val vm = MessagesViewModel(service)

        vm.contactSeller("fridge", makeOffer = true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.askOffer)
        vm.onOfferDialogShown()
        assertFalse(vm.uiState.value.askOffer)
    }

    @Test
    fun aSellerCannotBeContactedAboutTheirOwnListing() = runTest(dispatcher) {
        coEvery { service.start("mine", null) } throws MessagesException("this is your listing")
        val vm = MessagesViewModel(service)

        vm.contactSeller("mine", makeOffer = false)
        advanceUntilIdle()

        assertNull(vm.uiState.value.open)
        assertEquals("this is your listing", vm.uiState.value.message)
    }

    @Test
    fun messagesAndOffersAreSentAndTheAnswerIsShown() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation()
        coEvery { service.send("fridge__b1", "Hello") } returns conversation(
            messages = listOf(ConversationMessage("m1", "text", "Hello", null, true, 1))
        )
        coEvery { service.offer("fridge__b1", 8550) } returns conversation(canOffer = false)
        val vm = MessagesViewModel(service)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.send("  Hello ")
        advanceUntilIdle()
        assertEquals(listOf("Hello"), vm.uiState.value.open?.messages?.map { it.text })

        assertTrue(vm.makeOffer("85,50"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.open!!.canOffer)
        coVerify { service.offer("fridge__b1", 8550) }
    }

    @Test
    fun aWrongAmountIsNotSent() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation()
        val vm = MessagesViewModel(service)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        assertFalse(vm.makeOffer("eighty"))

        assertEquals("Write an amount, for example 85 or 85,50", vm.uiState.value.message)
        coVerify(exactly = 0) { service.offer(any(), any()) }
    }

    @Test
    fun theBackendRefusalOfAnOfferIsShown() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation()
        coEvery { service.offer("fridge__b1", 100) } throws MessagesException("an offer must be at least €50.00")
        val vm = MessagesViewModel(service)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.makeOffer("1")
        advanceUntilIdle()

        assertEquals("an offer must be at least €50.00", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun acceptingAndDecliningAnswerTheOffer() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation()
        coEvery { service.answerOffer("fridge__b1", any()) } returns conversation()
        val vm = MessagesViewModel(service)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.acceptOffer()
        advanceUntilIdle()
        vm.declineOffer()
        advanceUntilIdle()

        coVerify { service.answerOffer("fridge__b1", true) }
        coVerify { service.answerOffer("fridge__b1", false) }
    }

    @Test
    fun anAcceptedOfferIsPutInTheCartAndTheCartOpens() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation(offer = accepted())
        coEvery { cartService.addItem("used", "fridge", 1) } returns Cart()
        val vm = MessagesViewModel(service, cartStore)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.payOffer()
        advanceUntilIdle()

        coVerify { cartService.addItem("used", "fridge", 1) }
        assertTrue(vm.uiState.value.openCart)
        vm.onCartOpened()
        assertFalse(vm.uiState.value.openCart)
    }

    @Test
    fun aCartErrorWhilePayingIsShown() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returns conversation(offer = accepted())
        coEvery { cartService.addItem("used", "fridge", 1) } throws IOException("already sold")
        val vm = MessagesViewModel(service, cartStore)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.payOffer()
        advanceUntilIdle()

        assertEquals("already sold", vm.uiState.value.message)
        assertFalse(vm.uiState.value.openCart)
    }

    @Test
    fun theOpenConversationIsRefreshedAndClosedBackToTheInbox() = runTest(dispatcher) {
        coEvery { service.open("fridge__b1") } returnsMany listOf(
            conversation(),
            conversation(messages = listOf(ConversationMessage("m9", "text", "New", null, false, 9)))
        )
        coEvery { service.inbox() } returns Inbox(emptyList(), 0)
        val vm = MessagesViewModel(service)
        vm.openConversation("fridge__b1")
        advanceUntilIdle()

        vm.refreshOpen()
        advanceUntilIdle()
        assertEquals("New", vm.uiState.value.open?.messages?.single()?.text)

        vm.close()
        advanceUntilIdle()
        assertNull(vm.uiState.value.open)
        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }
}
