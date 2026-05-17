package com.thetwo.app.launch

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
import com.thetwo.app.network.ApiException
import com.thetwo.app.network.AuthRepository
import com.thetwo.app.network.CaptureRepository
import com.thetwo.app.network.CompanionRepository
import com.thetwo.app.network.toUserFacingMessage
import com.thetwo.app.session.PersistedSessionState
import com.thetwo.app.session.SessionLocalStore
import kotlinx.coroutines.launch

class LaunchViewModel(
    private val authRepository: AuthRepository,
    private val companionRepository: CompanionRepository,
    private val captureRepository: CaptureRepository,
    private val sessionLocalStore: SessionLocalStore,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(LaunchUiState())
        private set

    fun bootstrap() {
        if (uiState.isRestoring) return

        viewModelScope.launch {
            uiState = uiState.copy(
                isRestoring = true,
                errorMessage = null,
            )

            val localState = sessionLocalStore.read()
            val localSession = localState.authSession

            if (localSession == null) {
                uiState = LaunchUiState(
                    isRestoring = false,
                    resolution = LaunchResolution(
                        target = LaunchTarget.LOGIN,
                        restoredState = localState,
                    ),
                )
                return@launch
            }

            analyticsTracker.track(
                event = AnalyticsEvents.SESSION_RESTORE_STARTED,
                properties = mapOf(
                    "userId" to localSession.userId,
                    "screen" to "launch",
                ),
            )

            try {
                val remoteSession = authRepository.getCurrentSession(localSession.sessionToken)
                val companionProfile = companionRepository.fetchProfileOrNull(remoteSession.sessionToken)
                val recentCapture = captureRepository.getRecentCaptureOrNull(remoteSession.sessionToken)

                val restoredState = PersistedSessionState(
                    schemaVersion = PersistedSessionState.CURRENT_SCHEMA_VERSION,
                    authSession = remoteSession,
                    companionProfile = companionProfile,
                    recentCaptureReference = recentCapture,
                    arPrivacyAccepted = localState.arPrivacyAccepted,
                )

                val target = if (companionProfile == null) {
                    LaunchTarget.COMPANION_SETUP
                } else {
                    LaunchTarget.CHAT
                }

                analyticsTracker.track(
                    event = AnalyticsEvents.SESSION_RESTORE_SUCCEEDED,
                    properties = mapOf(
                        "userId" to remoteSession.userId,
                        "screen" to "launch",
                        "result" to target.name,
                    ),
                )

                uiState = LaunchUiState(
                    isRestoring = false,
                    resolution = LaunchResolution(
                        target = target,
                        restoredState = restoredState,
                    ),
                )
            } catch (error: Throwable) {
                if (error is ApiException && error.isUnauthorized) {
                    uiState = LaunchUiState(
                        isRestoring = false,
                        resolution = LaunchResolution(
                            target = LaunchTarget.LOGIN,
                            restoredState = localState,
                            clearAuthenticatedState = true,
                        ),
                    )
                    return@launch
                }

                analyticsTracker.track(
                    event = AnalyticsEvents.SESSION_RESTORE_FAILED,
                    properties = mapOf(
                        "userId" to localSession.userId,
                        "screen" to "launch",
                        "errorCode" to error.toErrorCode(),
                    ),
                )

                uiState = LaunchUiState(
                    isRestoring = false,
                    errorMessage = error.toUserFacingMessage("会话恢复失败，请重试。"),
                )
            }
        }
    }

    fun consumeResolution() {
        uiState = uiState.copy(resolution = null)
    }

    companion object {
        fun factory(
            authRepository: AuthRepository,
            companionRepository: CompanionRepository,
            captureRepository: CaptureRepository,
            sessionLocalStore: SessionLocalStore,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LaunchViewModel(
                    authRepository = authRepository,
                    companionRepository = companionRepository,
                    captureRepository = captureRepository,
                    sessionLocalStore = sessionLocalStore,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}

private fun Throwable.toErrorCode(): String {
    return if (this is ApiException) {
        errorCode
    } else {
        javaClass.simpleName.ifBlank { "UNKNOWN_ERROR" }
    }
}
