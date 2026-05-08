package com.thetwo.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/request-code")
    suspend fun requestCode(
        @Body request: RequestCodeRequest,
    ): Response<ApiEnvelope<RequestCodeData>>

    @POST("auth/verify-code")
    suspend fun verifyCode(
        @Body request: VerifyCodeRequest,
    ): Response<ApiEnvelope<AuthSession>>

    @GET("me")
    suspend fun getCurrentSession(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<AuthSession>>
}

interface CompanionApi {
    @GET("me/companion-profile")
    suspend fun getCompanionProfile(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<RemoteCompanionProfile>>

    @PUT("me/companion-profile")
    suspend fun putCompanionProfile(
        @Header("Authorization") authorization: String,
        @Body profile: RemoteCompanionProfile,
    ): Response<ApiEnvelope<RemoteCompanionProfile>>
}

interface ChatApi {
    @GET("chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<RemoteChatHistory>>

    @POST("chat/send")
    suspend fun sendMessage(
        @Header("Authorization") authorization: String,
        @Body request: RemoteChatSendRequest,
    ): Response<ApiEnvelope<RemoteChatSendResult>>

    @DELETE("chat/history")
    suspend fun clearChatHistory(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<BooleanActionResult>>
}

interface CaptureApi {
    @GET("me/recent-capture")
    suspend fun getRecentCapture(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<RemoteRecentCapture>>

    @PUT("me/recent-capture")
    suspend fun putRecentCapture(
        @Header("Authorization") authorization: String,
        @Body capture: RemoteRecentCapture,
    ): Response<ApiEnvelope<RemoteRecentCapture>>

    @DELETE("me/recent-capture")
    suspend fun clearRecentCapture(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<BooleanActionResult>>
}

interface AccountApi {
    @DELETE("me")
    suspend fun deleteAccount(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<BooleanActionResult>>
}
