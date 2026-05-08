package com.thetwo.app.chat

enum class MessageAuthor {
    USER,
    COMPANION,
}

enum class MessageStatus {
    SENT,
    SENDING,
    FAILED,
}

data class ChatMessage(
    val id: String,
    val author: MessageAuthor,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
)

enum class MockReplyMode {
    NORMAL,
    RESTRICTED,
}

data class RecentCaptureReference(
    val title: String,
    val summary: String,
    val storageLocation: String,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isReplying: Boolean = false,
    val isRestrictedMode: Boolean = false,
    val recentCaptureReference: RecentCaptureReference? = null,
)
