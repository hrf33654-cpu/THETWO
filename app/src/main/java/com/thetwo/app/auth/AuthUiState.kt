package com.thetwo.app.auth

data class AuthUiState(
    val email: String = "",
    val verificationCode: String = "",
    val consentAccepted: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
