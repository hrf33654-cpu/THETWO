package com.thetwo.app.network

data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T?,
    val errorCode: String?,
    val message: String,
    val details: Map<String, Any?>? = null,
)

data class RequestCodeRequest(
    val email: String,
)

data class RequestCodeData(
    val email: String,
    val devCode: String?,
    val deliveryMode: String,
)

data class VerifyCodeRequest(
    val email: String,
    val code: String,
)

data class AuthSession(
    val userId: String,
    val email: String,
    val sessionToken: String,
    val profileCompleted: Boolean,
)

data class RemoteCompanionProfile(
    val nickname: String,
    val tone: String,
    val personalityTags: List<String>,
    val interestTags: List<String>,
)

enum class RemoteChatMode {
    NORMAL,
    RESTRICTED,
}

data class RemoteChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val mode: RemoteChatMode,
    val clientMessageId: String?,
    val timestamp: String,
)

data class RemoteChatHistory(
    val messages: List<RemoteChatMessage>,
)

data class RemoteChatSendRequest(
    val message: String,
    val clientMessageId: String,
)

data class RemoteChatSendResult(
    val assistantMessage: String,
    val mode: RemoteChatMode,
    val timestamp: String,
)

data class RemoteRecentCapture(
    val title: String,
    val summary: String,
    val storageLocation: String,
    val updatedAt: String? = null,
)

data class BooleanActionResult(
    val cleared: Boolean? = null,
    val deleted: Boolean? = null,
)
