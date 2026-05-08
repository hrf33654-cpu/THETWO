package com.thetwo.app.session

import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.AuthSession

data class AppSessionState(
    val authSession: AuthSession? = null,
    val companionProfile: CompanionProfile? = null,
    val recentCaptureReference: RecentCaptureReference? = null,
    val arPrivacyAccepted: Boolean = false,
)
