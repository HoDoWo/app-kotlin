package com.hodowo.viewmodel

/**
 * A past chat, listed in the chat history menu: the appliance (model) it was
 * about, the first question and when it was last used.
 */
data class SavedChat(
    val id: String,
    val title: String,
    val applianceName: String?,
    val lastUpdated: Long,
    val messages: List<Message>
) {
    fun toSession(): ChatSession = ChatSession(
        id = id,
        title = title,
        lastUpdated = lastUpdated,
        applianceName = applianceName
    )
}

/**
 * Where the chats are kept, one list per user. The app keeps them on the phone
 * (see `FileChatHistoryStore`), so the history also works offline.
 */
interface ChatHistoryStore {
    /** The user's chats, newest first. */
    suspend fun load(userId: String): List<SavedChat>

    /** Adds the chat, or replaces the one with the same id. */
    suspend fun save(userId: String, chat: SavedChat)

    suspend fun delete(userId: String, chatId: String)
}

/** Appliance names that say nothing about the model: not shown in the history. */
private val GENERIC_APPLIANCE_NAMES = setOf("", "appliance", "household appliance", "new appliance")

private const val MAX_TITLE_LENGTH = 60

/**
 * Turns the current conversation into a [SavedChat], or null when the user has
 * not asked anything yet (an empty chat is not worth keeping).
 */
fun savedChatOf(
    chatId: String,
    messages: List<Message>,
    appliance: Appliance?
): SavedChat? {
    val finished = messages.filterNot { it.isStreaming }
    if (finished.none { it.isUser }) return null

    val firstQuestion = finished.firstOrNull { it.isUser && !it.text.isNullOrBlank() }?.text?.trim()
    val applianceName = (
            finished.lastOrNull { it.manualGuide != null }?.manualGuide?.applianceName
                ?: appliance?.let { listOfNotNull(it.brand, it.name, it.model).joinToString(" ") }
            )
        ?.trim()
        ?.takeUnless { it.lowercase() in GENERIC_APPLIANCE_NAMES }

    val title = when {
        firstQuestion != null -> firstQuestion.take(MAX_TITLE_LENGTH)
        applianceName != null -> "Photo of $applianceName"
        else -> "Photo of an appliance"
    }

    return SavedChat(
        id = chatId,
        title = title,
        applianceName = applianceName,
        // The time of the last message: reopening an old chat does not move it to the top.
        lastUpdated = finished.maxOf { it.timestamp },
        messages = finished
    )
}

/** This list with [chat] added or replaced, newest first. */
fun List<SavedChat>.upsert(chat: SavedChat): List<SavedChat> =
    (listOf(chat) + filterNot { it.id == chat.id }).sortedByDescending { it.lastUpdated }
