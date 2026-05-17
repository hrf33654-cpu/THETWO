package com.thetwo.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thetwo.app.chat.MockReplyMode
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.companion.CompanionProfile
import kotlinx.coroutines.delay
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiException(
    val errorCode: String,
    override val message: String,
    val statusCode: Int,
) : Exception(message) {
    val isUnauthorized: Boolean
        get() = errorCode == "UNAUTHORIZED" || statusCode == 401
}

data class RequestCodeResult(
    val message: String,
    val debugCodeHint: String?,
)

interface AuthRepository {
    suspend fun requestCode(email: String): RequestCodeResult
    suspend fun verifyCode(email: String, code: String): AuthSession
    suspend fun getCurrentSession(sessionToken: String): AuthSession
}

interface CompanionRepository {
    suspend fun fetchProfileOrNull(sessionToken: String): CompanionProfile?
    suspend fun saveProfile(sessionToken: String, profile: CompanionProfile): CompanionProfile
}

interface ChatRepository {
    suspend fun getHistory(sessionToken: String): List<RemoteChatMessage>
    suspend fun sendMessage(sessionToken: String, message: String, clientMessageId: String): RemoteChatSendResult
    suspend fun clearHistory(sessionToken: String)
}

interface CaptureRepository {
    suspend fun getRecentCaptureOrNull(sessionToken: String): RecentCaptureReference?
    suspend fun saveRecentCapture(sessionToken: String, reference: RecentCaptureReference): RecentCaptureReference
    suspend fun clearRecentCapture(sessionToken: String)
}

interface AccountRepository {
    suspend fun deleteAccount(sessionToken: String)
}

class RemoteAuthRepository(
    private val api: AuthApi,
    private val gson: Gson,
) : AuthRepository {
    override suspend fun requestCode(email: String): RequestCodeResult {
        val data = api.requestCode(RequestCodeRequest(email = email)).requireData(gson)
        return RequestCodeResult(
            message = if (data.deliveryMode == "SMTP") {
                "验证码已发送到邮箱，请查收后继续验证。"
            } else {
                "开发验证码已生成，请查看下方提示后继续验证。"
            },
            debugCodeHint = data.devCode,
        )
    }

    override suspend fun verifyCode(email: String, code: String): AuthSession {
        return api.verifyCode(
            VerifyCodeRequest(
                email = email,
                code = code,
            ),
        ).requireData(gson)
    }

    override suspend fun getCurrentSession(sessionToken: String): AuthSession {
        return api.getCurrentSession(sessionToken.bearer()).requireData(gson)
    }
}

class RemoteCompanionRepository(
    private val api: CompanionApi,
    private val gson: Gson,
) : CompanionRepository {
    override suspend fun fetchProfileOrNull(sessionToken: String): CompanionProfile? {
        return try {
            api.getCompanionProfile(sessionToken.bearer()).requireData(gson).toLocalProfile()
        } catch (error: ApiException) {
            if (error.statusCode == 404 && error.errorCode == "PROFILE_REQUIRED") {
                null
            } else {
                throw error
            }
        }
    }

    override suspend fun saveProfile(sessionToken: String, profile: CompanionProfile): CompanionProfile {
        return api.putCompanionProfile(
            authorization = sessionToken.bearer(),
            profile = profile.toRemoteProfile(),
        ).requireData(gson).toLocalProfile()
    }
}

class RemoteChatRepository(
    private val api: ChatApi,
    private val gson: Gson,
) : ChatRepository {
    override suspend fun getHistory(sessionToken: String): List<RemoteChatMessage> {
        return api.getChatHistory(sessionToken.bearer()).requireData(gson).messages
    }

    override suspend fun sendMessage(
        sessionToken: String,
        message: String,
        clientMessageId: String,
    ): RemoteChatSendResult {
        return api.sendMessage(
            authorization = sessionToken.bearer(),
            request = RemoteChatSendRequest(
                message = message,
                clientMessageId = clientMessageId,
            ),
        ).requireData(gson)
    }

    override suspend fun clearHistory(sessionToken: String) {
        api.clearChatHistory(sessionToken.bearer()).requireData(gson)
    }
}

class RemoteCaptureRepository(
    private val api: CaptureApi,
    private val gson: Gson,
) : CaptureRepository {
    override suspend fun getRecentCaptureOrNull(sessionToken: String): RecentCaptureReference? {
        return try {
            api.getRecentCapture(sessionToken.bearer()).requireData(gson).toRecentCaptureReference()
        } catch (error: ApiException) {
            if (error.statusCode == 404 && error.errorCode == "CAPTURE_UPDATE_FAILED") {
                null
            } else {
                throw error
            }
        }
    }

    override suspend fun saveRecentCapture(
        sessionToken: String,
        reference: RecentCaptureReference,
    ): RecentCaptureReference {
        return api.putRecentCapture(
            authorization = sessionToken.bearer(),
            capture = reference.toRemoteRecentCapture(),
        ).requireData(gson).toRecentCaptureReference()
    }

    override suspend fun clearRecentCapture(sessionToken: String) {
        api.clearRecentCapture(sessionToken.bearer()).requireData(gson)
    }
}

class RemoteAccountRepository(
    private val api: AccountApi,
    private val gson: Gson,
) : AccountRepository {
    override suspend fun deleteAccount(sessionToken: String) {
        api.deleteAccount(sessionToken.bearer()).requireData(gson)
    }
}

class MockAuthRepository : AuthRepository {
    override suspend fun requestCode(email: String): RequestCodeResult {
        delay(350)
        when {
            email.isBlank() -> throw IllegalArgumentException("请输入邮箱")
            !email.contains("@") -> throw IllegalArgumentException("请输入有效邮箱")
        }
        return RequestCodeResult(
            message = "开发验证码已生成",
            debugCodeHint = "123456",
        )
    }

    override suspend fun verifyCode(email: String, code: String): AuthSession {
        delay(500)
        when {
            email.isBlank() -> throw IllegalArgumentException("请输入邮箱")
            !email.contains("@") -> throw IllegalArgumentException("请输入有效邮箱")
            code.length < 4 -> throw IllegalArgumentException("请输入至少 4 位验证码")
        }
        return AuthSession(
            userId = "mock-user",
            email = email.trim(),
            sessionToken = "mock-session-token",
            profileCompleted = false,
        )
    }

    override suspend fun getCurrentSession(sessionToken: String): AuthSession {
        delay(150)
        return AuthSession(
            userId = "mock-user",
            email = "user@example.com",
            sessionToken = sessionToken,
            profileCompleted = false,
        )
    }
}

class MockChatRepository : ChatRepository {
    suspend fun generateReply(
        message: String,
        mode: MockReplyMode,
    ): String {
        delay(800)
        return if (mode == MockReplyMode.RESTRICTED) {
            "我会先把语气收得更稳一点。如果你现在很难受，先联系现实里的可信任对象或专业支持会更重要。我可以继续陪你聊一些更安全的话题。"
        } else {
            "我收到了。你慢慢说，我会继续在这里陪你。"
        }
    }

    override suspend fun getHistory(sessionToken: String): List<RemoteChatMessage> {
        return emptyList()
    }

    override suspend fun sendMessage(
        sessionToken: String,
        message: String,
        clientMessageId: String,
    ): RemoteChatSendResult {
        val mode = if (message.shouldRestrict()) RemoteChatMode.RESTRICTED else RemoteChatMode.NORMAL
        return RemoteChatSendResult(
            assistantMessage = generateReply(message, mode.toLocalMode()),
            mode = mode,
            timestamp = "mock-timestamp",
        )
    }

    override suspend fun clearHistory(sessionToken: String) = Unit
}

private fun String.shouldRestrict(): Boolean {
    val keywords = listOf("未成年", "初中", "高中", "不想活", "自杀", "轻生", "伤害自己")
    return keywords.any { contains(it, ignoreCase = true) }
}

private fun String.bearer(): String = "Bearer $this"

private fun CompanionProfile.toRemoteProfile(): RemoteCompanionProfile {
    return RemoteCompanionProfile(
        nickname = nickname,
        tone = tone,
        personalityTags = personalityTags,
        interestTags = interestTags,
    )
}

private fun RemoteCompanionProfile.toLocalProfile(): CompanionProfile {
    return CompanionProfile(
        nickname = nickname,
        tone = tone,
        personalityTags = personalityTags,
        interestTags = interestTags,
    )
}

private fun RemoteRecentCapture.toRecentCaptureReference(): RecentCaptureReference {
    return RecentCaptureReference(
        title = title,
        summary = summary,
        storageLocation = storageLocation,
    )
}

private fun RecentCaptureReference.toRemoteRecentCapture(): RemoteRecentCapture {
    return RemoteRecentCapture(
        title = title,
        summary = summary,
        storageLocation = storageLocation,
    )
}

private fun RemoteChatMode.toLocalMode(): MockReplyMode {
    return if (this == RemoteChatMode.RESTRICTED) {
        MockReplyMode.RESTRICTED
    } else {
        MockReplyMode.NORMAL
    }
}

private fun <T> Response<ApiEnvelope<T>>.requireData(gson: Gson): T {
    val payload = body()
    if (isSuccessful && payload?.success == true && payload.data != null) {
        return payload.data
    }

    val errorBody = errorBody()?.string()
    if (!errorBody.isNullOrBlank()) {
        val errorType = object : TypeToken<ApiEnvelope<Any>>() {}.type
        val parsed = runCatching { gson.fromJson<ApiEnvelope<Any>>(errorBody, errorType) }.getOrNull()
        if (parsed != null) {
            throw ApiException(
                errorCode = parsed.errorCode ?: "NETWORK_ERROR",
                message = parsed.message,
                statusCode = code(),
            )
        }
    }

    if (payload != null && !payload.success) {
        throw ApiException(
            errorCode = payload.errorCode ?: "NETWORK_ERROR",
            message = payload.message,
            statusCode = code(),
        )
    }

    throw ApiException(
        errorCode = "NETWORK_ERROR",
        message = payload?.message ?: "请求失败，请稍后重试。",
        statusCode = code(),
    )
}

fun Throwable.toUserFacingMessage(defaultMessage: String): String {
    if (this is ApiException) {
        return when {
            errorCode.equals("LLM_NOT_CONFIGURED", ignoreCase = true) ->
                "聊天模型还没有配置完成，请先检查后端 LLM 环境变量。"
            errorCode.equals("LLM_AUTH_FAILED", ignoreCase = true) ->
                "聊天模型鉴权失败，请检查 API Key、账号权限或套餐绑定。"
            errorCode.equals("LLM_RATE_LIMITED", ignoreCase = true) ||
                errorCode.contains("limit", ignoreCase = true) ||
                message.contains("usage limit exceeded", ignoreCase = true) ||
                message.contains("rate limit", ignoreCase = true) ->
                "聊天模型当前被额度或频率限制住了，请稍后再试，或先检查供应商套餐和余额。"
            errorCode.equals("LLM_MODEL_NOT_FOUND", ignoreCase = true) ->
                "聊天模型名称无效或当前套餐不支持这个模型，请检查后端的 LLM_MODEL 配置。"
            errorCode.equals("LLM_NETWORK_ERROR", ignoreCase = true) ->
                "后端暂时连不上模型服务，请检查 LLM_BASE_URL、网络或代理配置。"
            errorCode.equals("LLM_UPSTREAM_UNAVAILABLE", ignoreCase = true) ->
                "聊天模型服务暂时不可用，请稍后重试。"
            errorCode.equals("LLM_TIMEOUT", ignoreCase = true) ->
                "聊天模型响应超时了，请稍后再试。"
            message.isNotBlank() ->
                message
            else ->
                defaultMessage
        }
    }
    return when (this) {
        is ConnectException -> "网络连接失败，请检查网络后重试"
        is SocketTimeoutException -> "连接超时，请检查网络后重试"
        is UnknownHostException -> "无法连接服务器，请检查网络设置"
        is java.io.IOException -> "网络异常，请稍后重试"
        else -> message?.takeIf { it.isNotBlank() } ?: defaultMessage
    }
}
