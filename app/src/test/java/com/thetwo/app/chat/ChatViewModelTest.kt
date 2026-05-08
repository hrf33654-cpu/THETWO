package com.thetwo.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelTest {
    @Test
    fun `clearConversation resets messages with companion name`() {
        val viewModel = ChatViewModel()

        viewModel.clearConversation("飞樱")

        assertEquals(1, viewModel.uiState.messages.size)
        assertTrue(viewModel.uiState.messages.first().content.contains("飞樱"))
    }

    @Test
    fun `recent capture is appended to conversation`() {
        val viewModel = ChatViewModel()
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
