package com.thetwo.app.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: MockChatRepository = MockChatRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(
        ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = "welcome",
                    author = MessageAuthor.COMPANION,
                    content = "欢迎回来。聊天是当前 MVP 的主闭环，召唤页会作为增强入口补进来。",
                ),
            ),
        ),
    )
        private set

    fun updateDraft(value: String) {
        uiState = uiState.copy(draft = value)
    }

    fun sendMessage() {
        val content = uiState.draft.trim()
        if (content.isBlank() || uiState.isReplying) return

        val restrictedMode = shouldRestrict(content)
        val userMessage = ChatMessage(
            id = "user-${System.currentTimeMillis()}",
            author = MessageAuthor.USER,
            content = content,
            status = MessageStatus.SENT,
        )

        uiState = uiState.copy(
            draft = "",
            isReplying = true,
            isRestrictedMode = restrictedMode,
            messages = uiState.messages + userMessage,
        )

        viewModelScope.launch {
            val reply = repository.generateReply(
                message = content,
                mode = if (restrictedMode) MockReplyMode.RESTRICTED else MockReplyMode.NORMAL,
            )
            val companionMessage = ChatMessage(
                id = "companion-${System.currentTimeMillis()}",
                author = MessageAuthor.COMPANION,
                content = reply,
            )
            uiState = uiState.copy(
                isReplying = false,
                messages = uiState.messages + companionMessage,
            )
        }
    }

    fun onRecentCaptureRecorded(reference: RecentCaptureReference, companionName: String) {
        val captureMessage = ChatMessage(
            id = "capture-${System.currentTimeMillis()}",
            author = MessageAuthor.COMPANION,
            content = "$companionName 记住了你刚刚的召唤：${reference.summary}",
        )
        uiState = uiState.copy(
            recentCaptureReference = reference,
            messages = uiState.messages + captureMessage,
        )
    }

    fun clearRecentCaptureReference() {
        uiState = uiState.copy(recentCaptureReference = null)
    }

    fun clearConversation(companionName: String) {
        uiState = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = "reset-welcome",
                    author = MessageAuthor.COMPANION,
                    content = "$companionName 的聊天记录已清空。我们可以重新开始。",
                ),
            ),
        )
    }

    private fun shouldRestrict(content: String): Boolean {
        val keywords = listOf("未成年", "初中", "高中", "不想活", "自杀", "轻生", "伤害自己")
        return keywords.any { content.contains(it, ignoreCase = true) }
    }
}
