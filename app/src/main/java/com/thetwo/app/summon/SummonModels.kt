package com.thetwo.app.summon

import com.thetwo.app.chat.RecentCaptureReference

enum class SummonEntryState {
    PRIVACY_REQUIRED,
    CAMERA_PERMISSION_REQUIRED,
    CAMERA_PREVIEW_FALLBACK,
    SCREEN_ONLY_FALLBACK,
    EASYAR_TRACKING,
}

enum class CharacterModelState {
    LOADING,
    READY,
    FAILED,
    FALLBACK_2D,
}

enum class EasyArAvailability {
    NOT_BUNDLED,
    ARCHIVE_ONLY,
    LICENSE_MISSING,
    READY_FOR_TRACKING,
}

enum class EasyArTrackingState {
    IDLE,
    TRACKING,
    LOST,
    FAILED,
}

data class MarkerAsset(
    val name: String,
    val resourceName: String,
    val recommendedUsage: String,
)

data class CharacterAssetManifest(
    val sourceGlbImportNote: String,
    val expectedGlbName: String,
    val activeGlbName: String,
    val sourceAssetName: String,
    val mobileReady: Boolean,
    val idleAnimationRequired: Boolean,
)

data class CharacterTransformState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotationYDegrees: Float = 0f,
)

data class MarkerPoseState(
    val targetName: String,
    val poseMatrix: FloatArray,
    val projectionMatrix: DoubleArray,
)

data class SummonUiState(
    val entryState: SummonEntryState = SummonEntryState.PRIVACY_REQUIRED,
    val markerAsset: MarkerAsset = MarkerAsset(
        name = "Official marker v1",
        resourceName = "ar_marker_fantasy_v1",
        recommendedUsage = "Print the marker or display it on a second screen before EasyAR validation.",
    ),
    val characterAssetManifest: CharacterAssetManifest = CharacterAssetManifest(
        sourceGlbImportNote = "Temporary bundled cube placeholder for mobile 3D preview validation. The original character.glb is preserved separately until a mobile-ready asset is provided.",
        expectedGlbName = "placeholder_cube.glb",
        activeGlbName = "placeholder_cube.glb",
        sourceAssetName = "character.glb",
        mobileReady = false,
        idleAnimationRequired = false,
    ),
    val characterModelState: CharacterModelState = CharacterModelState.LOADING,
    val characterModelErrorMessage: String? = null,
    val isUsing3dPreview: Boolean = true,
    val characterModelLoadAttempt: Int = 0,
    val easyArAvailability: EasyArAvailability = EasyArAvailability.NOT_BUNDLED,
    val easyArTrackingState: EasyArTrackingState = EasyArTrackingState.IDLE,
    val markerPoseState: MarkerPoseState? = null,
    val trackingErrorMessage: String? = null,
    val isSavingCapture: Boolean = false,
    val statusMessage: String? = null,
    val pendingRecentCapture: RecentCaptureReference? = null,
)
