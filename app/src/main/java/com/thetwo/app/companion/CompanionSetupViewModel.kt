package com.thetwo.app.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class CompanionSetupViewModel : ViewModel() {
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

    fun buildProfile(): CompanionProfile? {
        if (uiState.nickname.isBlank()) {
            uiState = uiState.copy(errorMessage = "请先给角色起一个名字")
            return null
        }

        return CompanionProfile(
            nickname = uiState.nickname.trim(),
            tone = uiState.tone.trim(),
            personalityTags = uiState.personalityTagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            interestTags = uiState.interestTagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}
