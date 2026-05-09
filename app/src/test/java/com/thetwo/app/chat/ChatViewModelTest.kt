package com.thetwo.app.chat

import com.thetwo.app.analytics.NoOpAnalyticsTracker
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.CaptureRepository
import com.thetwo.app.network.ChatRepository
import com.thetwo.app.network.CompanionRepository
import com.thetwo.app.network.RemoteChatMessage
import com.thetwo.app.network.RemoteChatMode
import com.thetwo.app.network.RemoteChatSendResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {
    private val viewModel = ChatViewModel(
        chatRepository = object : ChatRepository {
            override suspend fun getHistory(sessionToken: String): List<RemoteChatMessage> = emptyList()

            override suspend fun sendMessage(
                sessionToken: String,
                message: String,
                clientMessageId: String,
            ): RemoteChatSendResult {
                return RemoteChatSendResult(
                    assistantMessage = "ok",
                    mode = RemoteChatMode.NORMAL,
                    timestamp = "now",
                )
            }

            override suspend fun clearHistory(sessionToken: String) = Unit
        },
        companionRepository = object : CompanionRepository {
            override suspend fun fetchProfileOrNull(sessionToken: String): CompanionProfile? = null

            override suspend fun saveProfile(sessionToken: String, profile: CompanionProfile): CompanionProfile = profile
        },
        captureRepository = object : CaptureRepository {
            override suspend fun getRecentCaptureOrNull(sessionToken: String): RecentCaptureReference? = null

            override suspend fun saveRecentCapture(
                sessionToken: String,
                reference: RecentCaptureReference,
            ): RecentCaptureReference = reference

            override suspend fun clearRecentCapture(sessionToken: String) = Unit
        },
        analyticsTracker = NoOpAnalyticsTracker(),
    )

    @Test
    fun `clearConversation resets messages with companion name`() {
        viewModel.clearConversation("飞樱")

        assertEquals(1, viewModel.uiState.messages.size)
        assertTrue(viewModel.uiState.messages.first().content.contains("飞樱"))
    }

    @Test
    fun `recent capture is appended to conversation`() {
        val reference = RecentCaptureReference(
            title = "最近一次召唤",
            summary = "你把飞樱放进了现实画面里。",
            storageLocation = "已保存到系统相册",
        )

        viewModel.onRecentCaptureRecorded(reference, "飞樱")

        assertEquals(reference, viewModel.uiState.recentCaptureReference)
        assertTrue(viewModel.uiState.messages.last().content.contains("飞樱"))
    }
}
