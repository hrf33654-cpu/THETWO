package com.thetwo.app.companion

data class CompanionSetupUiState(
    val nickname: String = "飞樱",
    val tone: String = "克制温柔",
    val personalityTagsInput: String = "温柔,细腻,会陪伴",
    val interestTagsInput: String = "二次元,夜空,拍照",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
