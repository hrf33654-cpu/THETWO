package com.thetwo.app.summon

import android.content.Context

private const val CHARACTER_ASSET_DIR = "models"
private const val SOURCE_CHARACTER_GLB = "character.glb"
private const val PLACEHOLDER_CHARACTER_GLB = "placeholder_cube.glb"

private val MOBILE_READY_CANDIDATES = listOf(
    "character_mobile.glb",
    "character_mobile_preview.glb",
    "character_static.glb",
    "character_mobile_static.glb",
)

object CharacterAssetSelector {
    fun resolve(context: Context): CharacterAssetManifest {
        val assetNames = runCatching {
            context.assets.list(CHARACTER_ASSET_DIR)?.toSet().orEmpty()
        }.getOrDefault(emptySet())

        val mobileCandidate = MOBILE_READY_CANDIDATES.firstOrNull(assetNames::contains)
        return if (mobileCandidate != null) {
            CharacterAssetManifest(
                sourceGlbImportNote = "A mobile-ready character asset candidate is bundled and selected for runtime validation.",
                expectedGlbName = mobileCandidate,
                activeGlbName = mobileCandidate,
                sourceAssetName = SOURCE_CHARACTER_GLB,
                mobileReady = true,
                idleAnimationRequired = true,
            )
        } else {
            CharacterAssetManifest(
                sourceGlbImportNote = "No mobile-ready character asset is bundled yet. The summon flow stays on placeholder_cube.glb while character.glb remains preserved for offline conversion.",
                expectedGlbName = PLACEHOLDER_CHARACTER_GLB,
                activeGlbName = PLACEHOLDER_CHARACTER_GLB,
                sourceAssetName = SOURCE_CHARACTER_GLB,
                mobileReady = false,
                idleAnimationRequired = false,
            )
        }
    }
}
