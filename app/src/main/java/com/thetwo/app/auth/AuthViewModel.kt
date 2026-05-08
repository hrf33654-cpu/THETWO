package com.thetwo.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thetwo.app.BuildConfig
import com.thetwo.app.network.AuthRepository
import com.thetwo.app.network.AuthSession
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    fun updateEmail(value: String) {
        uiState = uiState.copy(
            email = value,
            errorMessage = null,
            requestMessage = null,
            debugCodeHint = null,
            isCodeRequested = false,
        )
    }

    fun updateVerificationCode(value: String) {
        uiState = uiState.copy(
            verificationCode = value,
            errorMessage = null,
        )
    }

    fun updateConsentAccepted(accepted: Boolean) {
        uiState = uiState.copy(
            consentAccepted = accepted,
            errorMessage = null,
        )
    }

    fun requestCode() {
        if (uiState.isRequestingCode) return
        if (!uiState.consentAccepted) {
            uiState = uiState.copy(errorMessage = "请先同意隐私说明与数据处理说明。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isRequestingCode = true,
                errorMessage = null,
                requestMessage = null,
                debugCodeHint = null,
            )
            runCatching { repository.requestCode(uiState.email.trim()) }
                .onSuccess { result ->
                    uiState = uiState.copy(
                        isRequestingCode = false,
                        isCodeRequested = true,
                        requestMessage = result.message,
                        debugCodeHint = if (BuildConfig.DEBUG) result.debugCodeHint else null,
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isRequestingCode = false,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun verifyCode(onSuccess: (AuthSession) -> Unit) {
        if (uiState.isVerifying) return
        if (!uiState.consentAccepted) {
            uiState = uiState.copy(errorMessage = "请先同意隐私说明与数据处理说明。")
            return
        }
        if (!uiState.isCodeRequested) {
            uiState = uiState.copy(errorMessage = "请先发送验证码。")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isVerifying = true,
                errorMessage = null,
            )
            runCatching {
                repository.verifyCode(
                    email = uiState.email.trim(),
                    code = uiState.verificationCode.trim(),
                )
            }.onSuccess { session ->
                uiState = uiState.copy(isVerifying = false)
                onSuccess(session)
            }.onFailure { error ->
                uiState = uiState.copy(
                    isVerifying = false,
                    errorMessage = error.message,
                )
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(repository = repository)
            }
        }
    }
}
