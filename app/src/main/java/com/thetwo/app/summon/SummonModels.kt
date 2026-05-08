package com.thetwo.app.summon

import com.thetwo.app.chat.RecentCaptureReference

enum class SummonEntryState {
    PRIVACY_REQUIRED,
    CAMERA_PERMISSION_REQUIRED,
    CAMERA_PREVIEW_FALLBACK,
    SCREEN_ONLY_FALLBACK,
}

data class MarkerAsset(
    val name: String,
    val resourceName: String,
    val recommendedUsage: String,
)

data class CharacterAssetManifest(
    val sourceFbxPath: String,
    val expectedGlbName: String,
    val idleAnimationRequired: Boolean,
)

data class SummonUiState(
    val entryState: SummonEntryState = SummonEntryState.PRIVACY_REQUIRED,
    val markerAsset: MarkerAsset = MarkerAsset(
        name = "官方锚点图 v1",
        resourceName = "ar_marker_fantasy_v1",
        recommendedUsage = "推荐打印或在第二屏全屏展示，进入 AR 联调前先验证识别稳定性。",
    ),
    val characterAssetManifest: CharacterAssetManifest = CharacterAssetManifest(
        sourceFbxPath = "E:\\aic\\feiyingfbx\\未命名.fbx",
        expectedGlbName = "character.glb",
        idleAnimationRequired = true,
    ),
    val arServicesInstalled: Boolean = false,
    val isSavingCapture: Boolean = false,
    val statusMessage: String? = null,
    val pendingRecentCapture: RecentCaptureReference? = null,
)
