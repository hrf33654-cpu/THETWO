package com.thetwo.app.session

import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile

data class AppSessionState(
    val loginEmail: String? = null,
    val companionProfile: CompanionProfile? = null,
    val recentCaptureReference: RecentCaptureReference? = null,
    val arPrivacyAccepted: Boolean = false,
)
