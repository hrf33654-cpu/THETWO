package com.thetwo.app.companion

data class CompanionSetupUiState(
    val nickname: String = "飞樱",
    val tone: String = "温柔，稳定，带一点克制",
    val personalityTagsInput: String = "温柔, 敏锐, 忠诚",
    val interestTagsInput: String = "夜空, 音乐, 摄影",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
