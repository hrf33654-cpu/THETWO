package com.thetwo.app.auth

data class AuthUiState(
    val email: String = "",
    val verificationCode: String = "",
    val consentAccepted: Boolean = false,
    val ageConfirmed: Boolean = false,
    val isRequestingCode: Boolean = false,
    val isCodeRequested: Boolean = false,
    val isVerifying: Boolean = false,
    val requestMessage: String? = null,
    val debugCodeHint: String? = null,
    val errorMessage: String? = null,
    val resendCooldownSeconds: Int = 0,
)
