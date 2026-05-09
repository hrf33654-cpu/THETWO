package com.thetwo.app.network

import android.content.Context
import com.google.gson.Gson
import com.thetwo.app.analytics.AnalyticsTracker
import com.thetwo.app.analytics.DebugAnalyticsTracker
import com.thetwo.app.analytics.NoOpAnalyticsTracker
import com.thetwo.app.BuildConfig
import com.thetwo.app.session.SessionLocalStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(
    context: Context,
) {
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

    val sessionLocalStore: SessionLocalStore by lazy {
        SessionLocalStore(
            context = context,
            gson = gson,
        )
    }

    val analyticsTracker: AnalyticsTracker by lazy {
        if (BuildConfig.DEBUG) {
            DebugAnalyticsTracker()
        } else {
            NoOpAnalyticsTracker()
        }
    }

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
