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
            title = "Recent summon",
            summary = "A saved summon capture.",
            storageLocation = "Saved to gallery",
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

    @Test
    fun `retry character model load increments attempt and returns to loading`() {
        viewModel.markCharacterModelLoadFailed(
            authSession = null,
            errorCode = "MODEL_LOAD_FAILED",
            message = "boom",
        )

        viewModel.retryCharacterModelLoad(authSession = null)

        assertEquals(CharacterModelState.LOADING, viewModel.uiState.characterModelState)
        assertEquals(1, viewModel.uiState.characterModelLoadAttempt)
    }

    @Test
    fun `use 2d character fallback switches model state`() {
        viewModel.use2dCharacterFallback(authSession = null)

        assertEquals(CharacterModelState.FALLBACK_2D, viewModel.uiState.characterModelState)
        assertFalse(viewModel.uiState.isUsing3dPreview)
    }

    @Test
    fun `easyar tracking failure switches model state to fallback`() {
        viewModel.setEasyArTrackingFailed("Invalid Key")

        assertEquals(EasyArTrackingState.FAILED, viewModel.uiState.easyArTrackingState)
        assertEquals(CharacterModelState.FALLBACK_2D, viewModel.uiState.characterModelState)
        assertFalse(viewModel.uiState.isUsing3dPreview)
    }

    @Test
    fun `character asset manifest can be updated for mobile candidate`() {
        val manifest = CharacterAssetManifest(
            sourceGlbImportNote = "mobile candidate",
            expectedGlbName = "character_mobile.glb",
            activeGlbName = "character_mobile.glb",
            sourceAssetName = "character.glb",
            mobileReady = true,
            idleAnimationRequired = true,
        )

        viewModel.setCharacterAssetManifest(manifest)

        assertEquals(manifest, viewModel.uiState.characterAssetManifest)
        assertTrue(viewModel.uiState.characterAssetManifest.mobileReady)
    }

    @Test
    fun `show camera preview fallback after easyar failure keeps fallback model state`() {
        viewModel.setEasyArTrackingFailed("Invalid Key")
        viewModel.showCameraPreviewFallbackAfterEasyArFailure()

        assertEquals(SummonEntryState.CAMERA_PREVIEW_FALLBACK, viewModel.uiState.entryState)
        assertEquals(CharacterModelState.FALLBACK_2D, viewModel.uiState.characterModelState)
    }
}
