package com.thetwo.app.summon

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.thetwo.app.network.toUserFacingMessage
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val PAN_TO_WORLD_SCALE = 240f
private const val GLB_MAGIC = 0x46546C67
private const val GLB_JSON_CHUNK_TYPE = 0x4E4F534A
private const val FILAMENT_MAX_BONES = 256
private const val BASE_MARKER_MODEL_SIZE_METERS = 0.08f
private const val MARKER_SURFACE_LIFT_METERS = 0.04f

@Composable
fun CharacterModelViewport(
    modifier: Modifier = Modifier,
    assetPath: String,
    loadAttempt: Int,
    transformState: CharacterTransformState,
    markerPoseState: MarkerPoseState?,
    onLoadStarted: () -> Unit,
    onLoadSucceeded: () -> Unit,
    onLoadFailed: (errorCode: String, message: String) -> Unit,
) {
    key(loadAttempt) {
        var renderer by remember { mutableStateOf<CharacterModelRenderer?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                renderer?.destroy()
                renderer = null
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { context ->
                SurfaceView(context).also { surfaceView ->
                    renderer = CharacterModelRenderer(
                        context = context,
                        surfaceView = surfaceView,
                        onLoadStarted = onLoadStarted,
                        onLoadSucceeded = onLoadSucceeded,
                        onLoadFailed = onLoadFailed,
                    ).also { createdRenderer ->
                        createdRenderer.loadModel(assetPath)
                    }
                }
            },
            update = {
                renderer?.updateRenderState(transformState, markerPoseState)
            },
        )
    }
}

private class CharacterModelRenderer(
    private val context: Context,
    surfaceView: SurfaceView,
    private val onLoadStarted: () -> Unit,
    private val onLoadSucceeded: () -> Unit,
    private val onLoadFailed: (errorCode: String, message: String) -> Unit,
) {
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    private val modelViewer: ModelViewer

    private val choreographer = Choreographer.getInstance()
    private var transformState = CharacterTransformState()
    private var markerPoseState: MarkerPoseState? = null
    private var destroyed = false
    private var hasReportedReady = false
    private var animationStartNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return

            if (animationStartNanos == 0L) {
                animationStartNanos = frameTimeNanos
            }

            val elapsedSeconds = (frameTimeNanos - animationStartNanos) / 1_000_000_000f
            modelViewer.animator?.let { animator ->
                if (animator.animationCount > 0) {
                    val durationSeconds = animator.getAnimationDuration(0)
                    val animationTime = if (durationSeconds > 0f) {
                        elapsedSeconds % durationSeconds
                    } else {
                        0f
                    }
                    animator.applyAnimation(0, animationTime)
                    animator.updateBoneMatrices()
                }
            }

            applyTransform()
            modelViewer.render(frameTimeNanos)

            if (!hasReportedReady && modelViewer.asset != null && modelViewer.progress >= 1.0f) {
                hasReportedReady = true
                onLoadSucceeded()
            }

            choreographer.postFrameCallback(this)
        }
    }

    init {
        ensureFilamentInitialized()
        modelViewer = ModelViewer(
            surfaceView = surfaceView,
            engine = Engine.create(Engine.Backend.OPENGL),
            uiHelper = uiHelper,
        )
        surfaceView.setZOrderOnTop(true)
        surfaceView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        uiHelper.isOpaque = false
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
    }

    fun loadModel(assetPath: String) {
        if (destroyed) return
        onLoadStarted()
        hasReportedReady = false
        animationStartNanos = 0L

        runCatching {
            val directBuffer = loadAssetIntoDirectBuffer(assetPath)
            val boneCount = inspectMaxBoneCount(directBuffer)
            check(boneCount <= FILAMENT_MAX_BONES) {
                "${assetPath.substringAfterLast('/')} contains $boneCount bones, but Filament currently supports at most $FILAMENT_MAX_BONES bones per skin."
            }
            directBuffer.rewind()
            modelViewer.loadModelGlb(directBuffer)
            checkNotNull(modelViewer.asset) { "Model asset could not be created." }
            applyTransform()
            choreographer.postFrameCallback(frameCallback)
        }.onFailure { error ->
            onLoadFailed(
                when (error) {
                    is java.io.FileNotFoundException -> "MODEL_ASSET_MISSING"
                    is IllegalStateException,
                    is IllegalArgumentException,
                    -> "MODEL_UNSUPPORTED_BONE_COUNT"
                    else -> "MODEL_LOAD_FAILED"
                },
                error.toUserFacingMessage("3D模型加载失败"),
            )
        }
    }

    fun updateRenderState(
        state: CharacterTransformState,
        poseState: MarkerPoseState?,
    ) {
        transformState = state
        markerPoseState = poseState
        applyTransform()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
    }

    private fun loadAssetIntoDirectBuffer(assetPath: String): ByteBuffer {
        return context.assets.open(assetPath).use { inputStream ->
            val expectedSize = inputStream.available().coerceAtLeast(1)
            val directBuffer = ByteBuffer.allocateDirect(expectedSize).order(ByteOrder.nativeOrder())
            val chunk = ByteArray(DEFAULT_READ_CHUNK_SIZE)
            while (true) {
                val read = inputStream.read(chunk)
                if (read <= 0) break
                directBuffer.put(chunk, 0, read)
            }
            directBuffer.flip()
            directBuffer
        }
    }

    private fun applyTransform() {
        val asset = modelViewer.asset ?: return
        val poseState = markerPoseState ?: return
        val transformManager = modelViewer.engine.transformManager
        val instance = transformManager.getInstance(asset.root)
        if (instance == 0) return

        val center = asset.boundingBox.center
        val halfExtent = asset.boundingBox.halfExtent
        val maxExtent = maxOf(
            halfExtent[0] * 2f,
            halfExtent[1] * 2f,
            halfExtent[2] * 2f,
        ).coerceAtLeast(0.0001f)
        val baseScale = BASE_MARKER_MODEL_SIZE_METERS / maxExtent

        val toOrigin = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.translateM(this, 0, -center[0], -center[1], -center[2])
        }
        val scaled = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.scaleM(this, 0, baseScale * transformState.scale, baseScale * transformState.scale, baseScale * transformState.scale)
        }
        val rotated = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.rotateM(this, 0, 28f + transformState.rotationYDegrees, 0f, 1f, 0f)
            Matrix.rotateM(this, 0, -12f, 1f, 0f, 0f)
        }
        val translated = FloatArray(16).apply {
            Matrix.setIdentityM(this, 0)
            Matrix.translateM(
                this,
                0,
                transformState.offsetX / PAN_TO_WORLD_SCALE,
                -transformState.offsetY / PAN_TO_WORLD_SCALE,
                MARKER_SURFACE_LIFT_METERS,
            )
        }

        val targetPose = poseState.poseMatrix.copyOf()
        val poseAdjusted = FloatArray(16)
        val temp = FloatArray(16)
        val transform = FloatArray(16)
        Matrix.multiplyMM(temp, 0, scaled, 0, toOrigin, 0)
        Matrix.multiplyMM(transform, 0, rotated, 0, temp, 0)
        Matrix.multiplyMM(temp, 0, translated, 0, transform, 0)
        Matrix.multiplyMM(poseAdjusted, 0, targetPose, 0, temp, 0)
        transformManager.setTransform(instance, poseAdjusted)
        modelViewer.camera.setCustomProjection(
            poseState.projectionMatrix,
            0.1,
            100.0,
        )
    }

    companion object {
        @Volatile
        private var filamentInitialized = false

        private fun ensureFilamentInitialized() {
            if (filamentInitialized) return
            synchronized(this) {
                if (filamentInitialized) return
                Utils.init()
                filamentInitialized = true
            }
        }
    }
}

private const val DEFAULT_READ_CHUNK_SIZE = 64 * 1024

private fun inspectMaxBoneCount(buffer: ByteBuffer): Int {
    val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    require(duplicate.remaining() >= 20) { "Invalid GLB header." }
    val magic = duplicate.int
    require(magic == GLB_MAGIC) { "Not a valid GLB file." }
    duplicate.int // version
    duplicate.int // length

    while (duplicate.remaining() >= 8) {
        val chunkLength = duplicate.int
        val chunkType = duplicate.int
        require(chunkLength >= 0 && chunkLength <= duplicate.remaining()) { "Invalid GLB chunk length." }
        if (chunkType == GLB_JSON_CHUNK_TYPE) {
            val jsonBytes = ByteArray(chunkLength)
            duplicate.get(jsonBytes)
            val json = JSONObject(String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t'))
            val skins = json.optJSONArray("skins") ?: return 0
            var maxBones = 0
            for (index in 0 until skins.length()) {
                val skin = skins.optJSONObject(index) ?: continue
                val joints = skin.optJSONArray("joints") ?: continue
                if (joints.length() > maxBones) {
                    maxBones = joints.length()
                }
            }
            return maxBones
        } else {
            duplicate.position(duplicate.position() + chunkLength)
        }
    }
    throw IllegalArgumentException("GLB JSON chunk not found.")
}
