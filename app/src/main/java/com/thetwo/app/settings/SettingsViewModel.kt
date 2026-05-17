package com.thetwo.app.settings

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
import com.thetwo.app.network.AccountRepository
import com.thetwo.app.network.ApiException
import com.thetwo.app.network.AuthSession
import com.thetwo.app.network.CaptureRepository
import com.thetwo.app.network.ChatRepository
import com.thetwo.app.network.toUserFacingMessage
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isWorking: Boolean = false,
    val feedbackMessage: String? = null,
)

class SettingsViewModel(
    private val chatRepository: ChatRepository,
    private val captureRepository: CaptureRepository,
    private val accountRepository: AccountRepository,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun clearRecentCapture(
        authSession: AuthSession?,
        onSuccess: () -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession
        if (session == null) {
            uiState = uiState.copy(feedbackMessage = "登录态已失效，请重新登录。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isWorking = true, feedbackMessage = null)
            runCatching { captureRepository.clearRecentCapture(session.sessionToken) }
                .onSuccess {
                    analyticsTracker.track(
                        event = AnalyticsEvents.RECENT_CAPTURE_CLEARED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "settings",
                            "result" to "success",
                        ),
                    )
                    uiState = uiState.copy(
                        isWorking = false,
                        feedbackMessage = "已清除 App 内最近作品回流记录，不会删除系统相册文件。",
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    handleFailure(error, onUnauthorized)
                }
        }
    }

    fun clearChatHistory(
        authSession: AuthSession?,
        onSuccess: () -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession
        if (session == null) {
            uiState = uiState.copy(feedbackMessage = "登录态已失效，请重新登录。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isWorking = true, feedbackMessage = null)
            runCatching { chatRepository.clearHistory(session.sessionToken) }
                .onSuccess {
                    analyticsTracker.track(
                        event = AnalyticsEvents.CHAT_HISTORY_CLEARED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "settings",
                            "result" to "success",
                        ),
                    )
                    uiState = uiState.copy(
                        isWorking = false,
                        feedbackMessage = "已清空远端聊天记录，本地聊天已重置为欢迎消息。",
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    handleFailure(error, onUnauthorized)
                }
        }
    }

    fun logout(
        authSession: AuthSession?,
        onSuccess: () -> Unit,
    ) {
        authSession?.let { session ->
            analyticsTracker.track(
                event = AnalyticsEvents.LOGOUT_SUCCESS,
                properties = mapOf(
                    "userId" to session.userId,
                    "screen" to "settings",
                    "result" to "success",
                ),
            )
        }
        uiState = uiState.copy(
            isWorking = false,
            feedbackMessage = "已退出登录。",
        )
        onSuccess()
    }

    fun deleteAccount(
        authSession: AuthSession?,
        onSuccess: () -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession
        if (session == null) {
            uiState = uiState.copy(feedbackMessage = "登录态已失效，请重新登录。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isWorking = true, feedbackMessage = null)
            runCatching { accountRepository.deleteAccount(session.sessionToken) }
                .onSuccess {
                    analyticsTracker.track(
                        event = AnalyticsEvents.ACCOUNT_DELETED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "settings",
                            "result" to "success",
                        ),
                    )
                    uiState = uiState.copy(
                        isWorking = false,
                        feedbackMessage = "账号数据已删除，正在返回登录页。",
                    )
                    onSuccess()
                }
                .onFailure { error ->
                    handleFailure(error, onUnauthorized)
                }
        }
    }

    private fun handleFailure(
        error: Throwable,
        onUnauthorized: () -> Unit,
    ) {
        if (error is ApiException && error.isUnauthorized) {
            uiState = uiState.copy(isWorking = false)
            onUnauthorized()
        } else {
            uiState = uiState.copy(
                isWorking = false,
                feedbackMessage = error.toUserFacingMessage("操作失败，请稍后重试。"),
            )
        }
    }

    companion object {
        fun factory(
            chatRepository: ChatRepository,
            captureRepository: CaptureRepository,
            accountRepository: AccountRepository,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    chatRepository = chatRepository,
                    captureRepository = captureRepository,
                    accountRepository = accountRepository,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}
