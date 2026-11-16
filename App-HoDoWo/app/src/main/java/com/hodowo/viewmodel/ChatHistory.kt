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
    val messages: List<Message>,
    /** The recognised appliance (name, brand, model), so a reopened chat keeps its context. */
    val applianceKind: String? = null,
    val applianceBrand: String? = null,
    val applianceModel: String? = null
) {
    fun toSession(): ChatSession = ChatSession(
        id = id,
        title = title,
        lastUpdated = lastUpdated,
        applianceName = applianceName
    )

    /** The appliance the chat was about, given back to the AI when the chat is reopened. */
    fun appliance(): Appliance? = when {
        applianceKind != null -> Appliance(name = applianceKind, brand = applianceBrand, model = applianceModel, confidence = 1f)
        applianceName != null -> Appliance(name = applianceName, brand = null, model = null, confidence = 1f)
        else -> null
    }
}

/**
 * Where the chats are kept, one list per user: on the phone (`FileChatHistoryStore`),
 * so the history works offline, and in the user's account (`SyncedChatHistoryStore`),
 * so it is there again on a new phone.
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
        messages = finished,
        applianceKind = appliance?.name?.trim()?.ifEmpty { null },
        applianceBrand = appliance?.brand?.trim()?.ifEmpty { null },
        applianceModel = appliance?.model?.trim()?.ifEmpty { null }
    )
}

/**
 * The chat in which [guide] was written, newest first: same manual id, or the same
 * problem on the same appliance for guides saved without an id.
 */
fun List<SavedChat>.chatWithGuide(guide: ManualGuide): SavedChat? = firstOrNull { chat ->
    chat.messages.any { message ->
        val written = message.manualGuide ?: return@any false
        if (guide.manualId != null && written.manualId != null) {
            written.manualId == guide.manualId
        } else {
            written.issueTitle.equals(guide.issueTitle, ignoreCase = true) &&
                written.applianceName.equals(guide.applianceName, ignoreCase = true)
        }
    }
}

/** This list with [chat] added or replaced, newest first. */
fun List<SavedChat>.upsert(chat: SavedChat): List<SavedChat> =
    (listOf(chat) + filterNot { it.id == chat.id }).sortedByDescending { it.lastUpdated }

/**
 * The conversation sent to the AI with a new question: every finished message,
 * with photos and guides written out as text, so it knows what the chat is about.
 */
fun conversationHistory(messages: List<Message>, appliance: Appliance?): List<ChatMessageHistory> {
    val photoOf = appliance?.let { listOfNotNull(it.brand, it.name, it.model).joinToString(" ") }
        ?.takeUnless { it.isBlank() } ?: "the appliance"
    return messages.filterNot { it.isStreaming }.mapNotNull { message ->
        val text = message.text?.trim()?.takeIf { it.isNotEmpty() }
        val content = when {
            message.isUser && message.imageUri != null ->
                listOfNotNull("[Sent a photo of $photoOf]", text).joinToString(" ")
            message.manualGuide != null -> guideAsText(message.manualGuide, text)
            else -> text
        }
        content?.let { ChatMessageHistory(role = if (message.isUser) "user" else "assistant", content = it) }
    }
}

private fun guideAsText(guide: ManualGuide, text: String?): String {
    val steps = guide.steps.mapIndexed { index, step -> "${index + 1}. ${step.title}" }
    return listOfNotNull(
        text ?: guide.overview,
        "Guide: ${guide.issueTitle} (${guide.applianceName})",
        steps.takeIf { it.isNotEmpty() }?.joinToString("\n")
    ).joinToString("\n")
}
