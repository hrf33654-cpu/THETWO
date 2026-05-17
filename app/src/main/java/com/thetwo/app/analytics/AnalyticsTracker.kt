package com.thetwo.app.analytics

import android.util.Log
import com.thetwo.app.BuildConfig

interface AnalyticsTracker {
    fun track(
        event: String,
        properties: Map<String, String> = emptyMap(),
    )
}

object AnalyticsEvents {
    const val LOGIN_REQUEST_CODE_SUCCESS = "login_request_code_success"
    const val LOGIN_VERIFY_SUCCESS = "login_verify_success"
    const val COMPANION_PROFILE_SAVED = "companion_profile_saved"
    const val CHAT_SEND_SUCCESS = "chat_send_success"
    const val CHAT_SEND_FAILED = "chat_send_failed"
    const val CHAT_RESTRICTED_MODE_ENTERED = "chat_restricted_mode_entered"
    const val SUMMON_OPENED = "summon_opened"
    const val CHARACTER_MODEL_LOAD_STARTED = "character_model_load_started"
    const val CHARACTER_MODEL_LOAD_SUCCEEDED = "character_model_load_succeeded"
    const val CHARACTER_MODEL_LOAD_FAILED = "character_model_load_failed"
    const val CHARACTER_MODEL_FALLBACK_USED = "character_model_fallback_used"
    const val CAPTURE_SAVED_LOCAL = "capture_saved_local"
    const val CAPTURE_SYNC_SUCCESS = "capture_sync_success"
    const val CAPTURE_SYNC_FAILED = "capture_sync_failed"
    const val CHAT_HISTORY_CLEARED = "chat_history_cleared"
    const val RECENT_CAPTURE_CLEARED = "recent_capture_cleared"
    const val LOGOUT_SUCCESS = "logout_success"
    const val ACCOUNT_DELETED = "account_deleted"
    const val SESSION_RESTORE_STARTED = "session_restore_started"
    const val SESSION_RESTORE_SUCCEEDED = "session_restore_succeeded"
    const val SESSION_RESTORE_FAILED = "session_restore_failed"
    const val SESSION_UNAUTHORIZED_CLEARED = "session_unauthorized_cleared"
}

class DebugAnalyticsTracker : AnalyticsTracker {
    override fun track(
        event: String,
        properties: Map<String, String>,
    ) {
        if (!BuildConfig.DEBUG) return
        val payload = properties.entries.joinToString(separator = ", ") { (key, value) ->
            "$key=$value"
        }
        Log.d("THETWO_ANALYTICS", "event=$event${if (payload.isBlank()) "" else " | $payload"}")
    }
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(
        event: String,
        properties: Map<String, String>,
    ) = Unit
}
