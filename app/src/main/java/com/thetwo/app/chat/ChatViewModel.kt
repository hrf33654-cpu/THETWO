package com.thetwo.app.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thetwo.app.analytics.AnalyticsEvents
import com.thetwo.app.analytics.AnalyticsTracker
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.ApiException
import com.thetwo.app.network.AuthSession
import com.thetwo.app.network.CaptureRepository
import com.thetwo.app.network.ChatRepository
import com.thetwo.app.network.CompanionRepository
import com.thetwo.app.network.RemoteChatMessage
import com.thetwo.app.network.RemoteChatMode
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val companionRepository: CompanionRepository,
    private val captureRepository: CaptureRepository,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(
        ChatUiState(
            messages = defaultWelcomeMessages(),
        ),
    )
        private set

    private var loadedSessionToken: String? = null

    fun updateDraft(value: String) {
        uiState = uiState.copy(draft = value, errorMessage = null)
    }

    fun bootstrapSession(
        authSession: AuthSession?,
        onProfileLoaded: (CompanionProfile) -> Unit,
        onProfileRequired: () -> Unit,
        onRecentCaptureLoaded: (RecentCaptureReference?) -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession ?: return
        if (loadedSessionToken == session.sessionToken) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isInitializing = true,
                errorMessage = null,
            )

            val profile = try {
                companionRepository.fetchProfileOrNull(session.sessionToken)
            } catch (error: Throwable) {
                handleBootstrapFailure(error, onUnauthorized)
                return@launch
            }

            if (profile == null) {
                uiState = uiState.copy(isInitializing = false)
                onProfileRequired()
                return@launch
            }

            onProfileLoaded(profile)

            var historyError: String? = null
            val history = try {
                chatRepository.getHistory(session.sessionToken)
            } catch (error: Throwable) {
                if (error is ApiException && error.isUnauthorized) {
                    handleBootstrapFailure(error, onUnauthorized)
                    return@launch
                }
                historyError = error.message ?: "聊天历史加载失败。"
                emptyList()
            }

            val recentCapture = try {
                captureRepository.getRecentCaptureOrNull(session.sessionToken)
            } catch (error: Throwable) {
                if (error is ApiException && error.isUnauthorized) {
                    handleBootstrapFailure(error, onUnauthorized)
                    return@launch
                }
                historyError = historyError ?: (error.message ?: "最近作品回流加载失败。")
                null
            }

            val mappedMessages = if (history.isEmpty()) {
                defaultWelcomeMessages(profile.nickname)
            } else {
                history.map(::toLocalMessage)
            }

            uiState = uiState.copy(
                messages = mappedMessages,
                isRestrictedMode = history.lastOrNull()?.mode == RemoteChatMode.RESTRICTED,
                isInitializing = false,
                errorMessage = historyError,
                recentCaptureReference = recentCapture,
            )
            loadedSessionToken = session.sessionToken
            onRecentCaptureLoaded(recentCapture)
        }
    }

    fun sendMessage(
        authSession: AuthSession?,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession
        val content = uiState.draft.trim()
        if (content.isBlank() || uiState.isReplying) return
        if (session == null) {
            uiState = uiState.copy(errorMessage = "登录态已失效，请重新登录。")
            return
        }

        val clientMessageId = UUID.randomUUID().toString()
        val userMessage = ChatMessage(
            id = clientMessageId,
            author = MessageAuthor.USER,
            content = content,
            status = MessageStatus.SENDING,
        )

        uiState = uiState.copy(
            draft = "",
            isReplying = true,
            errorMessage = null,
            messages = uiState.messages + userMessage,
        )

        viewModelScope.launch {
            runCatching {
                chatRepository.sendMessage(
                    sessionToken = session.sessionToken,
                    message = content,
                    clientMessageId = clientMessageId,
                )
            }.onSuccess { reply ->
                val updatedMessages = uiState.messages.map { message ->
                    if (message.id == clientMessageId) {
                        message.copy(
                            status = MessageStatus.SENT,
                            mode = reply.mode,
                        )
                    } else {
                        message
                    }
                }
                val companionMessage = ChatMessage(
                    id = "assistant-${reply.timestamp}-$clientMessageId",
                    author = MessageAuthor.COMPANION,
                    content = reply.assistantMessage,
                    mode = reply.mode,
                )
                uiState = uiState.copy(
                    isReplying = false,
                    isRestrictedMode = reply.mode == RemoteChatMode.RESTRICTED,
                    messages = updatedMessages + companionMessage,
                )
                analyticsTracker.track(
                    event = AnalyticsEvents.CHAT_SEND_SUCCESS,
                    properties = mapOf(
                        "userId" to session.userId,
                        "screen" to "chat",
                        "result" to "success",
                        "mode" to reply.mode.name,
                    ),
                )
                if (reply.mode == RemoteChatMode.RESTRICTED) {
                    analyticsTracker.track(
                        event = AnalyticsEvents.CHAT_RESTRICTED_MODE_ENTERED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "chat",
                            "mode" to reply.mode.name,
                        ),
                    )
                }
            }.onFailure { error ->
                if (error is ApiException && error.isUnauthorized) {
                    resetSessionState()
                    onUnauthorized()
                } else {
                    uiState = uiState.copy(
                        isReplying = false,
                        errorMessage = error.message ?: "发送失败，请稍后重试。",
                        messages = uiState.messages.markMessageStatus(
                            messageId = clientMessageId,
                            status = MessageStatus.FAILED,
                        ),
                    )
                    analyticsTracker.track(
                        event = AnalyticsEvents.CHAT_SEND_FAILED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "chat",
                            "result" to "failed",
                            "errorCode" to error.toAnalyticsErrorCode(),
                        ),
                    )
                }
            }
        }
    }

    fun retryMessage(
        messageId: String,
        authSession: AuthSession?,
        onUnauthorized: () -> Unit,
    ) {
        val original = uiState.messages.firstOrNull { it.id == messageId } ?: return
        if (original.author != MessageAuthor.USER) return

        uiState = uiState.copy(
            draft = original.content,
            messages = uiState.messages.filterNot { it.id == messageId },
        )
        sendMessage(authSession, onUnauthorized)
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
            messages = defaultWelcomeMessages(companionName),
        )
    }

    fun resetSessionState() {
        loadedSessionToken = null
        uiState = ChatUiState(
            messages = defaultWelcomeMessages(),
        )
    }

    private fun handleBootstrapFailure(
        error: Throwable,
        onUnauthorized: () -> Unit,
    ) {
        if (error is ApiException && error.isUnauthorized) {
            resetSessionState()
            onUnauthorized()
        } else {
            uiState = uiState.copy(
                isInitializing = false,
                errorMessage = error.message ?: "远端会话加载失败。",
            )
        }
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            companionRepository: CompanionRepository,
            captureRepository: CaptureRepository,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatRepository = chatRepository,
                    companionRepository = companionRepository,
                    captureRepository = captureRepository,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}

private fun List<ChatMessage>.markMessageStatus(
    messageId: String,
    status: MessageStatus,
): List<ChatMessage> {
    return map { message ->
        if (message.id == messageId) {
            message.copy(status = status)
        } else {
            message
        }
    }
}

private fun defaultWelcomeMessages(companionName: String = "角色"): List<ChatMessage> {
    return listOf(
        ChatMessage(
            id = "welcome",
            author = MessageAuthor.COMPANION,
            content = "欢迎回来。$companionName 会先把聊天作为主闭环，召唤页作为增强入口继续接入。",
        ),
    )
}

private fun toLocalMessage(remoteMessage: RemoteChatMessage): ChatMessage {
    return ChatMessage(
        id = remoteMessage.id,
        author = if (remoteMessage.role == "USER") MessageAuthor.USER else MessageAuthor.COMPANION,
        content = remoteMessage.content,
        status = MessageStatus.SENT,
        mode = remoteMessage.mode,
    )
}

private fun Throwable.toAnalyticsErrorCode(): String {
    return if (this is ApiException) {
        errorCode
    } else {
        javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
    }
}
