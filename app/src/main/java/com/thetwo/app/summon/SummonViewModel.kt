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
        uiState = uiState.copy(
            entryState = SummonEntryState.CAMERA_PERMISSION_REQUIRED,
            easyArTrackingState = EasyArTrackingState.IDLE,
            markerPoseState = null,
            trackingErrorMessage = null,
        )
    }

    fun showCameraPreviewFallback() {
        uiState = uiState.copy(
            entryState = SummonEntryState.CAMERA_PREVIEW_FALLBACK,
            easyArTrackingState = EasyArTrackingState.IDLE,
            markerPoseState = null,
            trackingErrorMessage = null,
        )
    }

    fun showCameraPreviewFallbackAfterEasyArFailure() {
        uiState = uiState.copy(
            entryState = SummonEntryState.CAMERA_PREVIEW_FALLBACK,
            markerPoseState = null,
            characterModelState = CharacterModelState.FALLBACK_2D,
            isUsing3dPreview = false,
        )
    }

    fun showEasyArTracking() {
        uiState = uiState.copy(
            entryState = SummonEntryState.EASYAR_TRACKING,
            easyArTrackingState = EasyArTrackingState.IDLE,
            markerPoseState = null,
            trackingErrorMessage = null,
        )
    }

    fun showScreenOnlyFallback() {
        uiState = uiState.copy(
            entryState = SummonEntryState.SCREEN_ONLY_FALLBACK,
            easyArTrackingState = EasyArTrackingState.IDLE,
            markerPoseState = null,
            trackingErrorMessage = null,
        )
    }

    fun showScreenOnlyFallbackAfterEasyArFailure() {
        uiState = uiState.copy(
            entryState = SummonEntryState.SCREEN_ONLY_FALLBACK,
            markerPoseState = null,
            characterModelState = CharacterModelState.FALLBACK_2D,
            isUsing3dPreview = false,
        )
    }

    fun setEasyArAvailability(availability: EasyArAvailability) {
        uiState = uiState.copy(easyArAvailability = availability)
    }

    fun setCharacterAssetManifest(manifest: CharacterAssetManifest) {
        uiState = uiState.copy(characterAssetManifest = manifest)
    }

    fun setEasyArTrackingIdle() {
        uiState = uiState.copy(
            easyArTrackingState = EasyArTrackingState.IDLE,
            markerPoseState = null,
            trackingErrorMessage = null,
        )
    }

    fun setEasyArTrackingStarted(markerPoseState: MarkerPoseState) {
        uiState = uiState.copy(
            easyArTrackingState = EasyArTrackingState.TRACKING,
            markerPoseState = markerPoseState,
            trackingErrorMessage = null,
        )
    }

    fun setEasyArTrackingUpdated(markerPoseState: MarkerPoseState) {
        uiState = uiState.copy(
            easyArTrackingState = EasyArTrackingState.TRACKING,
            markerPoseState = markerPoseState,
            trackingErrorMessage = null,
        )
    }

    fun setEasyArTrackingLost() {
        uiState = uiState.copy(
            easyArTrackingState = EasyArTrackingState.LOST,
            markerPoseState = null,
        )
    }

    fun setEasyArTrackingFailed(message: String) {
        uiState = uiState.copy(
            easyArTrackingState = EasyArTrackingState.FAILED,
            markerPoseState = null,
            trackingErrorMessage = message,
            characterModelState = CharacterModelState.FALLBACK_2D,
            isUsing3dPreview = false,
        )
    }

    fun setSavingCapture(saving: Boolean) {
        uiState = uiState.copy(isSavingCapture = saving)
    }

    fun setStatusMessage(message: String?) {
        uiState = uiState.copy(statusMessage = message)
    }

    fun markCharacterModelLoadStarted(authSession: AuthSession?) {
        uiState = uiState.copy(
            characterModelState = CharacterModelState.LOADING,
            characterModelErrorMessage = null,
            isUsing3dPreview = true,
        )
        analyticsTracker.track(
            event = AnalyticsEvents.CHARACTER_MODEL_LOAD_STARTED,
            properties = buildMap {
                put("screen", "summon")
                authSession?.userId?.let { put("userId", it) }
            },
        )
    }

    fun markCharacterModelLoadSucceeded(authSession: AuthSession?) {
        uiState = uiState.copy(
            characterModelState = CharacterModelState.READY,
            characterModelErrorMessage = null,
            isUsing3dPreview = true,
        )
        analyticsTracker.track(
            event = AnalyticsEvents.CHARACTER_MODEL_LOAD_SUCCEEDED,
            properties = buildMap {
                put("screen", "summon")
                put("result", "success")
                authSession?.userId?.let { put("userId", it) }
            },
        )
    }

    fun markCharacterModelLoadFailed(
        authSession: AuthSession?,
        errorCode: String,
        message: String,
    ) {
        uiState = uiState.copy(
            characterModelState = CharacterModelState.FAILED,
            characterModelErrorMessage = message,
            isUsing3dPreview = false,
        )
        analyticsTracker.track(
            event = AnalyticsEvents.CHARACTER_MODEL_LOAD_FAILED,
            properties = buildMap {
                put("screen", "summon")
                put("result", "failed")
                put("errorCode", errorCode)
                authSession?.userId?.let { put("userId", it) }
            },
        )
    }

    fun retryCharacterModelLoad(authSession: AuthSession?) {
        uiState = uiState.copy(
            characterModelState = CharacterModelState.LOADING,
            characterModelErrorMessage = null,
            isUsing3dPreview = true,
            characterModelLoadAttempt = uiState.characterModelLoadAttempt + 1,
        )
        markCharacterModelLoadStarted(authSession)
    }

    fun use2dCharacterFallback(authSession: AuthSession?) {
        uiState = uiState.copy(
            characterModelState = CharacterModelState.FALLBACK_2D,
            isUsing3dPreview = false,
        )
        analyticsTracker.track(
            event = AnalyticsEvents.CHARACTER_MODEL_FALLBACK_USED,
            properties = buildMap {
                put("screen", "summon")
                put("result", "fallback_2d")
                authSession?.userId?.let { put("userId", it) }
            },
        )
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
                statusMessage = "Screenshot saved locally, but the session is no longer valid.",
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
                    statusMessage = "Capture saved and synced back into chat.",
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
                        statusMessage = "Screenshot saved, but recent capture sync failed. You can retry here.",
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
