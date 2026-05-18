package com.thetwo.app.chat

import com.thetwo.app.analytics.NoOpAnalyticsTracker
import com.thetwo.app.companion.CompanionProfile
import com.thetwo.app.network.CaptureRepository
import com.thetwo.app.network.ChatRepository
import com.thetwo.app.network.CompanionRepository
import com.thetwo.app.network.RemoteChatMessage
import com.thetwo.app.network.RemoteChatMode
import com.thetwo.app.network.RemoteChatSendResult
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
        viewModel.clearConversation("Sakura")

        assertEquals(1, viewModel.uiState.messages.size)
        assertTrue(viewModel.uiState.messages.first().content.contains("Sakura"))
    }

    @Test
    fun `recent capture is appended to conversation`() {
        val reference = RecentCaptureReference(
            title = "Recent summon",
            summary = "You saved a soft blue summon scene.",
            storageLocation = "Saved to gallery",
        )

        viewModel.onRecentCaptureRecorded(reference, "Sakura")

        assertEquals(reference, viewModel.uiState.recentCaptureReference)
        assertTrue(viewModel.uiState.messages.last().content.contains("Sakura"))
    }

    @Test
    fun `sanitizeDisplayedMessage removes think blocks`() {
        val sanitized = sanitizeDisplayedMessage("<think>internal trace</think>Morning.")

        assertEquals("Morning.", sanitized)
    }

    @Test
    fun `sanitizeDisplayedMessage falls back when content is empty after cleanup`() {
        val sanitized = sanitizeDisplayedMessage("<think>internal trace</think>")

        assertEquals("...", sanitized)
    }
}
