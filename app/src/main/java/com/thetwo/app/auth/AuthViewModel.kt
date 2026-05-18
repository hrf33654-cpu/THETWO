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
import com.thetwo.app.analytics.AnalyticsEvents
import com.thetwo.app.analytics.AnalyticsTracker
import com.thetwo.app.network.AuthRepository
import com.thetwo.app.network.AuthSession
import com.thetwo.app.network.toUserFacingMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker,
) : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    private var resendCountdownJob: Job? = null

    fun updateEmail(value: String) {
        resendCountdownJob?.cancel()
        uiState = uiState.copy(
            email = value,
            errorMessage = null,
            requestMessage = null,
            debugCodeHint = null,
            isCodeRequested = false,
            verificationCode = "",
            resendCooldownSeconds = 0,
        )
    }

    fun updateVerificationCode(value: String) {
        uiState = uiState.copy(
            verificationCode = value.filter(Char::isDigit).take(6),
            errorMessage = null,
        )
    }

    fun updateConsentAccepted(accepted: Boolean) {
        uiState = uiState.copy(
            consentAccepted = accepted,
            errorMessage = null,
        )
    }

    fun updateAgeConfirmed(confirmed: Boolean) {
        uiState = uiState.copy(
            ageConfirmed = confirmed,
            errorMessage = null,
        )
    }

    fun requestCode(onSuccess: () -> Unit = {}) {
        if (uiState.isRequestingCode) return
        if (!hasRequiredConsent()) {
            uiState = uiState.copy(errorMessage = "请先确认隐私协议，并勾选 18 岁及以上声明。")
            return
        }
        if (uiState.email.isBlank()) {
            uiState = uiState.copy(errorMessage = "请输入邮箱。")
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
                    analyticsTracker.track(
                        event = AnalyticsEvents.LOGIN_REQUEST_CODE_SUCCESS,
                        properties = mapOf(
                            "screen" to "login",
                            "result" to "success",
                        ),
                    )
                    uiState = uiState.copy(
                        isRequestingCode = false,
                        isCodeRequested = true,
                        verificationCode = "",
                        requestMessage = result.message,
                        debugCodeHint = if (BuildConfig.DEBUG) result.debugCodeHint else null,
                    )
                    startResendCountdown()
                    onSuccess()
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isRequestingCode = false,
                        errorMessage = error.toUserFacingMessage("验证码发送失败，请稍后再试。"),
                    )
                }
        }
    }

    fun verifyCode(onSuccess: (AuthSession) -> Unit) {
        if (uiState.isVerifying) return
        if (!hasRequiredConsent()) {
            uiState = uiState.copy(errorMessage = "请先确认隐私协议，并勾选 18 岁及以上声明。")
            return
        }
        if (!uiState.isCodeRequested) {
            uiState = uiState.copy(errorMessage = "请先获取验证码。")
            return
        }
        if (uiState.verificationCode.length < 6) {
            uiState = uiState.copy(errorMessage = "请输入 6 位验证码。")
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
                analyticsTracker.track(
                    event = AnalyticsEvents.LOGIN_VERIFY_SUCCESS,
                    properties = mapOf(
                        "userId" to session.userId,
                        "screen" to "verify",
                        "result" to "success",
                    ),
                )
                uiState = uiState.copy(isVerifying = false)
                onSuccess(session)
            }.onFailure { error ->
                uiState = uiState.copy(
                    isVerifying = false,
                    errorMessage = error.toUserFacingMessage("验证码校验失败，请重试。"),
                )
            }
        }
    }

    private fun hasRequiredConsent(): Boolean {
        return uiState.consentAccepted && uiState.ageConfirmed
    }

    private fun startResendCountdown() {
        resendCountdownJob?.cancel()
        resendCountdownJob = viewModelScope.launch {
            for (seconds in 60 downTo 1) {
                uiState = uiState.copy(resendCooldownSeconds = seconds)
                delay(1_000)
            }
            uiState = uiState.copy(resendCooldownSeconds = 0)
        }
    }

    companion object {
        fun factory(
            repository: AuthRepository,
            analyticsTracker: AnalyticsTracker,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    repository = repository,
                    analyticsTracker = analyticsTracker,
                )
            }
        }
    }
}
