package com.thetwo.app.companion

data class CompanionProfile(
    val nickname: String,
    val tone: String,
    val personalityTags: List<String>,
    val interestTags: List<String>,
)
