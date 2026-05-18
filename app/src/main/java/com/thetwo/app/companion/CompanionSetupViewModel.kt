package com.thetwo.app.companion

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
import com.thetwo.app.network.AuthSession
import com.thetwo.app.network.CompanionRepository
import com.thetwo.app.network.toUserFacingMessage
import kotlinx.coroutines.launch

class CompanionSetupViewModel(
    private val repository: CompanionRepository,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(CompanionSetupUiState())
        private set

    fun updateNickname(value: String) {
        uiState = uiState.copy(nickname = value, errorMessage = null)
    }

    fun updateTone(value: String) {
        uiState = uiState.copy(tone = value, errorMessage = null)
    }

    fun updatePersonalityTagsInput(value: String) {
        uiState = uiState.copy(personalityTagsInput = value, errorMessage = null)
    }

    fun updateInterestTagsInput(value: String) {
        uiState = uiState.copy(interestTagsInput = value, errorMessage = null)
    }

    fun submitProfile(
        authSession: AuthSession?,
        onSuccess: (CompanionProfile) -> Unit,
        onUnauthorized: () -> Unit,
    ) {
        if (uiState.isSaving) return
        val profile = buildProfileOrNull() ?: return
        if (authSession == null) {
            uiState = uiState.copy(errorMessage = "登录状态已失效，请重新登录。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isSaving = true, errorMessage = null)
            runCatching {
                repository.saveProfile(
                    sessionToken = authSession.sessionToken,
                    profile = profile,
                )
            }.onSuccess { savedProfile ->
                analyticsTracker.track(
                    event = AnalyticsEvents.COMPANION_PROFILE_SAVED,
                    properties = mapOf(
                        "userId" to authSession.userId,
                        "screen" to "companion_setup",
                        "result" to "success",
                    ),
                )
                uiState = uiState.copy(isSaving = false)
                onSuccess(savedProfile)
            }.onFailure { error ->
                if (error is ApiException && error.isUnauthorized) {
                    uiState = uiState.copy(isSaving = false)
                    onUnauthorized()
                } else {
                    uiState = uiState.copy(
                        isSaving = false,
                        errorMessage = error.toUserFacingMessage("保存同伴资料失败，请稍后再试。"),
                    )
                }
            }
        }
    }

    private fun buildProfileOrNull(): CompanionProfile? {
        if (uiState.nickname.isBlank()) {
            uiState = uiState.copy(errorMessage = "先给你的同伴起个名字吧。")
            return null
        }

        return CompanionProfile(
            nickname = uiState.nickname.trim(),
            tone = uiState.tone.trim(),
            personalityTags = uiState.personalityTagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            interestTags = uiState.interestTagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    companion object {
        fun factory(
            repository: CompanionRepository,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CompanionSetupViewModel(
                    repository = repository,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}
