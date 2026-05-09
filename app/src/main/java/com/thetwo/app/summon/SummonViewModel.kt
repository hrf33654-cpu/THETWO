package com.thetwo.app.summon

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
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.network.ApiException
import com.thetwo.app.network.AuthSession
import com.thetwo.app.network.CaptureRepository
import kotlinx.coroutines.launch

class SummonViewModel(
    private val captureRepository: CaptureRepository,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(SummonUiState())
        private set

    fun requirePrivacyAck() {
        uiState = uiState.copy(entryState = SummonEntryState.PRIVACY_REQUIRED)
    }

    fun showCameraPermissionRequired() {
        uiState = uiState.copy(entryState = SummonEntryState.CAMERA_PERMISSION_REQUIRED)
    }

    fun showCameraPreviewFallback() {
        uiState = uiState.copy(entryState = SummonEntryState.CAMERA_PREVIEW_FALLBACK)
    }

    fun showScreenOnlyFallback() {
        uiState = uiState.copy(entryState = SummonEntryState.SCREEN_ONLY_FALLBACK)
    }

    fun setArServicesInstalled(installed: Boolean) {
        uiState = uiState.copy(arServicesInstalled = installed)
    }

    fun setSavingCapture(saving: Boolean) {
        uiState = uiState.copy(isSavingCapture = saving)
    }

    fun setStatusMessage(message: String?) {
        uiState = uiState.copy(statusMessage = message)
    }

    fun trackSummonOpened(authSession: AuthSession?) {
        analyticsTracker.track(
            event = AnalyticsEvents.SUMMON_OPENED,
            properties = buildMap {
                put("screen", "summon")
                authSession?.userId?.let { put("userId", it) }
            },
        )
    }

    fun trackLocalCaptureSaved(authSession: AuthSession?) {
        analyticsTracker.track(
            event = AnalyticsEvents.CAPTURE_SAVED_LOCAL,
            properties = buildMap {
                put("screen", "summon")
                put("result", "success")
                authSession?.userId?.let { put("userId", it) }
            },
        )
    }

    fun syncRecentCapture(
        authSession: AuthSession?,
        reference: RecentCaptureReference,
        onSuccess: (RecentCaptureReference) -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        val session = authSession
        if (session == null) {
            uiState = uiState.copy(
                isSavingCapture = false,
                pendingRecentCapture = reference,
                statusMessage = "截图已保存，但登录态已失效，无法同步作品回流。",
            )
            return
        }

        viewModelScope.launch {
            setSavingCapture(true)
            runCatching {
                captureRepository.saveRecentCapture(session.sessionToken, reference)
            }.onSuccess { savedReference ->
                analyticsTracker.track(
                    event = AnalyticsEvents.CAPTURE_SYNC_SUCCESS,
                    properties = mapOf(
                        "userId" to session.userId,
                        "screen" to "summon",
                        "result" to "success",
                    ),
                )
                uiState = uiState.copy(
                    isSavingCapture = false,
                    pendingRecentCapture = null,
                    statusMessage = "截图与作品回流已同步，正在返回聊天页。",
                )
                onSuccess(savedReference)
            }.onFailure { error ->
                if (error is ApiException && error.isUnauthorized) {
                    uiState = uiState.copy(isSavingCapture = false)
                    onUnauthorized()
                } else {
                    analyticsTracker.track(
                        event = AnalyticsEvents.CAPTURE_SYNC_FAILED,
                        properties = mapOf(
                            "userId" to session.userId,
                            "screen" to "summon",
                            "result" to "failed",
                            "errorCode" to error.toAnalyticsErrorCode(),
                        ),
                    )
                    uiState = uiState.copy(
                        isSavingCapture = false,
                        pendingRecentCapture = reference,
                        statusMessage = "截图已保存，但作品回流同步失败，可留在当前页重试。",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            captureRepository: CaptureRepository,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SummonViewModel(
                    captureRepository = captureRepository,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}

private fun Throwable.toAnalyticsErrorCode(): String {
    return if (this is ApiException) {
        errorCode
    } else {
        javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
    }
}
