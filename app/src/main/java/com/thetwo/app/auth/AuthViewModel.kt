package com.thetwo.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: MockAuthRepository = MockAuthRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    fun updateEmail(value: String) {
        uiState = uiState.copy(email = value, errorMessage = null)
    }

    fun updateVerificationCode(value: String) {
        uiState = uiState.copy(verificationCode = value, errorMessage = null)
    }

    fun updateConsentAccepted(accepted: Boolean) {
        uiState = uiState.copy(consentAccepted = accepted, errorMessage = null)
    }

    fun submit(onSuccess: () -> Unit) {
        if (uiState.isSubmitting) return

        viewModelScope.launch {
            uiState = uiState.copy(isSubmitting = true, errorMessage = null)
            val result = repository.login(
                email = uiState.email,
                code = uiState.verificationCode,
                consentAccepted = uiState.consentAccepted,
            )
            uiState = if (result.isSuccess) {
                uiState.copy(isSubmitting = false)
            } else {
                uiState.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message,
                )
            }
            if (result.isSuccess) {
                onSuccess()
            }
        }
    }

    fun currentEmail(): String = uiState.email.trim()
}
