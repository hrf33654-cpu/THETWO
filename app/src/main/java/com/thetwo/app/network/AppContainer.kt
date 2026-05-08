package com.thetwo.app.network

import com.google.gson.Gson
import com.thetwo.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer {
    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    },
                )
            }
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    private val companionApi: CompanionApi by lazy { retrofit.create(CompanionApi::class.java) }
    private val chatApi: ChatApi by lazy { retrofit.create(ChatApi::class.java) }
    private val captureApi: CaptureApi by lazy { retrofit.create(CaptureApi::class.java) }
    private val accountApi: AccountApi by lazy { retrofit.create(AccountApi::class.java) }

    val authRepository: AuthRepository by lazy { RemoteAuthRepository(authApi, gson) }
    val companionRepository: CompanionRepository by lazy { RemoteCompanionRepository(companionApi, gson) }
    val chatRepository: ChatRepository by lazy { RemoteChatRepository(chatApi, gson) }
    val captureRepository: CaptureRepository by lazy { RemoteCaptureRepository(captureApi, gson) }
    val accountRepository: AccountRepository by lazy { RemoteAccountRepository(accountApi, gson) }
}
