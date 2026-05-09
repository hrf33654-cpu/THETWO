package com.thetwo.app.summon

import com.thetwo.app.analytics.NoOpAnalyticsTracker
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.network.CaptureRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummonViewModelTest {
    private val viewModel = SummonViewModel(
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
    fun `sync recent capture keeps pending state when auth session is missing`() {
        val reference = RecentCaptureReference(
            title = "最近一次召唤",
            summary = "你把飞樱带进了画面里。",
            storageLocation = "已保存到系统相册",
        )

        viewModel.syncRecentCapture(
            authSession = null,
            reference = reference,
            onSuccess = {},
            onUnauthorized = {},
        )

        assertEquals(reference, viewModel.uiState.pendingRecentCapture)
        assertNotNull(viewModel.uiState.statusMessage)
        assertFalse(viewModel.uiState.isSavingCapture)
    }

    @Test
    fun `show screen only fallback updates entry state`() {
        viewModel.showScreenOnlyFallback()

        assertTrue(viewModel.uiState.entryState == SummonEntryState.SCREEN_ONLY_FALLBACK)
    }
}
