package com.thetwo.app.chat

import com.thetwo.app.network.RemoteChatMode

enum class MessageAuthor {
    USER,
    COMPANION,
}

enum class MessageStatus {
    SENT,
    SENDING,
    FAILED,
}

enum class MockReplyMode {
    NORMAL,
    RESTRICTED,
}

data class ChatMessage(
    val id: String,
    val author: MessageAuthor,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val mode: RemoteChatMode = RemoteChatMode.NORMAL,
)

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
    val isInitializing: Boolean = false,
    val errorMessage: String? = null,
    val recentCaptureReference: RecentCaptureReference? = null,
)
