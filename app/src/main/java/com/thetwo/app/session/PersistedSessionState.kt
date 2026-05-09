package com.thetwo.app.session

import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.AuthSession

data class PersistedSessionState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val authSession: AuthSession? = null,
    val companionProfile: CompanionProfile? = null,
    val recentCaptureReference: RecentCaptureReference? = null,
    val arPrivacyAccepted: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}
