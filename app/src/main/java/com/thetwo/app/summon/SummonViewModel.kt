package com.thetwo.app.summon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SummonViewModel : ViewModel() {
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
}
