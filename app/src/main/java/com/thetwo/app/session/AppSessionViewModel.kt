package com.thetwo.app.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile

class AppSessionViewModel : ViewModel() {
    var uiState by mutableStateOf(AppSessionState())
        private set

    fun setLoginEmail(email: String) {
        uiState = uiState.copy(loginEmail = email)
    }

    fun setCompanionProfile(profile: CompanionProfile) {
        uiState = uiState.copy(companionProfile = profile)
    }

    fun setRecentCapture(reference: RecentCaptureReference) {
        uiState = uiState.copy(recentCaptureReference = reference)
    }

    fun clearRecentCapture() {
        uiState = uiState.copy(recentCaptureReference = null)
    }

    fun acceptArPrivacy() {
        uiState = uiState.copy(arPrivacyAccepted = true)
    }
}
