package com.hodowo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
fun interface ChatClient {
    suspend fun send(message: String): String
}

class SkeletonViewModel(
    private val chatClient: ChatClient = OpenRouterChatClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    var reply by mutableStateOf("")
        private set

    fun send(message: String) {
        if (message.isBlank()) {
            reply = "Enter a message."
            return
        }

        viewModelScope.launch(ioDispatcher) {
            reply = try {
                chatClient.send(message)
            } catch (exception: Exception) {
                "Error: ${exception.message ?: "The message could not be sent"}"
            }
        }
    }
}
