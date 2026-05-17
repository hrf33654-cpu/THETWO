package com.thetwo.app.summon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import cn.easyar.Buffer
import cn.easyar.CameraDevice
import cn.easyar.CameraDeviceFocusMode
import cn.easyar.DelayedCallbackScheduler
import cn.easyar.FunctorOfVoid
import cn.easyar.ImageTarget
import cn.easyar.ImageTracker
import cn.easyar.ImageTrackerResult
import cn.easyar.InputFrame
import cn.easyar.InputFrameFork
import cn.easyar.InputFrameToFeedbackFrameAdapter
import cn.easyar.Matrix44F
import cn.easyar.OutputFrame
import cn.easyar.OutputFrameBuffer
import cn.easyar.PixelFormat
import cn.easyar.TargetInstance
import cn.easyar.TargetStatus
import cn.easyar.Vec2I
import com.thetwo.app.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

private const val EASYAR_TAG = "THETWO_EASYAR"
private const val TARGET_NAME = "official_marker_v1"
private const val TARGET_PHYSICAL_SCALE_METERS = 0.16f
private const val PREVIEW_FRAME_INTERVAL_MS = 66L

class EasyArSession(private val context: Context) {

    private var scheduler: DelayedCallbackScheduler? = null
    private var cameraDevice: CameraDevice? = null
    private var imageTracker: ImageTracker? = null
    private var inputFork: InputFrameFork? = null
    private var feedbackAdapter: InputFrameToFeedbackFrameAdapter? = null
    private var outputFrameBuffer: OutputFrameBuffer? = null
    private var textureView: TextureView? = null
    private var trackingTargetId: String? = null
    private var running = AtomicBoolean(false)
    private var hasTracking = false
    private var lastPreviewRenderMs = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    var onTrackingStarted: ((MarkerPoseState) -> Unit)? = null
    var onTrackingUpdated: ((MarkerPoseState) -> Unit)? = null
    var onTrackingLost: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun bindTextureView(view: TextureView) {
        textureView = view
        view.isOpaque = true
    }

    fun start(width: Int, height: Int): Boolean {
        if (running.get()) return true

        val initializationResult = EasyArInitializer.initializeIfPossible(context)
        if (!initializationResult.initialized) {
            onError?.invoke(initializationResult.errorMessage ?: "EasyAR initialize failed")
            return false
        }

        val localScheduler = DelayedCallbackScheduler()
        scheduler = localScheduler

        val localCamera = CameraDevice()
        cameraDevice = localCamera
        if (!localCamera.openWithIndex(0)) {
            onError?.invoke("Failed to open EasyAR camera")
            disposeAll()
            return false
        }
        localCamera.setFocusMode(CameraDeviceFocusMode.Continousauto)
        localCamera.setBufferCapacity(8)
        localCamera.setSize(Vec2I(width.coerceAtLeast(640), height.coerceAtLeast(480)))

        val localTracker = ImageTracker.create()
        imageTracker = localTracker
        localTracker.setSimultaneousNum(1)
        localTracker.setResultPostProcessing(true, true)

        val localFork = InputFrameFork.create(2)
        inputFork = localFork
        localCamera.inputFrameSource().connect(localFork.input())

        localFork.output(0).setHandler { inputFrame ->
            renderCameraPreview(inputFrame)
        }

        val localFeedbackAdapter = InputFrameToFeedbackFrameAdapter.create()
        feedbackAdapter = localFeedbackAdapter
        localFork.output(1).connect(localFeedbackAdapter.input())
        localTracker.outputFrameSource().connect(localFeedbackAdapter.sideInput())
        localFeedbackAdapter.output().connect(localTracker.feedbackFrameSink())

        val localOutputFrameBuffer = OutputFrameBuffer.create()
        outputFrameBuffer = localOutputFrameBuffer
        localTracker.outputFrameSource().connect(localOutputFrameBuffer.input())
        localOutputFrameBuffer.signalOutput().setHandler(FunctorOfVoid {
            localOutputFrameBuffer.peek()?.let { outputFrame ->
                handleOutputFrame(outputFrame)
            }
        })

        val markerPath = saveMarkerImage() ?: run {
            onError?.invoke("Failed to prepare official marker asset")
            disposeAll()
            return false
        }

        val target = ImageTarget.createFromImageFile(
            markerPath,
            0,
            TARGET_NAME,
            "",
            "",
            TARGET_PHYSICAL_SCALE_METERS,
        ) ?: run {
            onError?.invoke("Failed to create EasyAR image target")
            disposeAll()
            return false
        }
        trackingTargetId = target.name()

        localTracker.loadTarget(
            target,
            localScheduler,
            cn.easyar.FunctorOfVoidFromTargetAndBool { targetRef, status ->
                if (!status) {
                    Log.e(EASYAR_TAG, "EasyAR marker target load failed name=${targetRef.name()}")
                    onError?.invoke("Failed to load EasyAR marker target")
                    return@FunctorOfVoidFromTargetAndBool
                }
                trackingTargetId = targetRef.name()
                Log.i(EASYAR_TAG, "EasyAR marker target loaded name=$trackingTargetId")
            },
        )

        while (localScheduler.runOne()) {}

        if (!localTracker.start()) {
            onError?.invoke("Failed to start EasyAR image tracker")
            disposeAll()
            return false
        }

        if (!localCamera.start()) {
            onError?.invoke("Failed to start EasyAR camera")
            disposeAll()
            return false
        }

        hasTracking = false
        running.set(true)
        return true
    }

    fun stop() {
        disposeAll()
    }

    fun isRunning(): Boolean = running.get()

    private fun handleOutputFrame(outputFrame: OutputFrame) {
        val trackerResult = outputFrame.results()
            .filterIsInstance<ImageTrackerResult>()
            .firstOrNull()

        val targetInstance = trackerResult
            ?.targetInstances()
            ?.firstOrNull { target ->
                target.status() == TargetStatus.Tracking &&
                    (trackingTargetId == null || target.target().name() == trackingTargetId)
            }

        if (targetInstance == null) {
            if (hasTracking) {
                hasTracking = false
                Log.i(EASYAR_TAG, "EasyAR tracking lost target=$trackingTargetId")
                onTrackingLost?.invoke()
            }
            return
        }

        val poseState = buildMarkerPoseState(outputFrame.inputFrame(), targetInstance) ?: run {
            if (hasTracking) {
                hasTracking = false
                onTrackingLost?.invoke()
            }
            return
        }

        if (hasTracking) {
            onTrackingUpdated?.invoke(poseState)
        } else {
            hasTracking = true
            Log.i(EASYAR_TAG, "EasyAR tracking acquired target=${poseState.targetName}")
            onTrackingStarted?.invoke(poseState)
        }
    }

    private fun buildMarkerPoseState(
        inputFrame: InputFrame?,
        targetInstance: TargetInstance,
    ): MarkerPoseState? {
        val frame = inputFrame ?: return null
        if (!frame.hasCameraParameters()) return null

        val projection = frame.cameraParameters().projection(
            0.1f,
            100f,
            1.0f,
            0,
            false,
            false,
        )

        return MarkerPoseState(
            targetName = targetInstance.target().name(),
            poseMatrix = targetInstance.pose().toFloatArray(),
            projectionMatrix = projection.toDoubleArray(),
        )
    }

    private fun renderCameraPreview(inputFrame: InputFrame) {
        val now = System.currentTimeMillis()
        if (now - lastPreviewRenderMs < PREVIEW_FRAME_INTERVAL_MS) return
        lastPreviewRenderMs = now

        val bitmap = inputFrame.toBitmap() ?: return
        val targetView = textureView ?: return

        mainHandler.post {
            if (!targetView.isAvailable) return@post
            val canvas = targetView.lockCanvas() ?: return@post
            try {
                drawPreviewBitmap(canvas, bitmap)
            } finally {
                targetView.unlockCanvasAndPost(canvas)
            }
        }
    }

    private fun drawPreviewBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
    ) {
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, null, false)
        canvas.save()
        canvas.translate(canvas.width.toFloat(), 0f)
        canvas.rotate(90f)

        val sourceAspect = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetAspect = canvas.height.toFloat() / canvas.width.toFloat()
        val destination = if (sourceAspect > targetAspect) {
            val scaledWidth = canvas.width * sourceAspect
            val left = (canvas.height - scaledWidth) / 2f
            Rect(
                left.toInt(),
                0,
                (left + scaledWidth).toInt(),
                canvas.width,
            )
        } else {
            val scaledHeight = canvas.height / sourceAspect
            val top = (canvas.width - scaledHeight) / 2f
            Rect(
                0,
                top.toInt(),
                canvas.height,
                (top + scaledHeight).toInt(),
            )
        }

        canvas.drawBitmap(
            bitmap,
            Rect(0, 0, bitmap.width, bitmap.height),
            destination,
            null,
        )
        canvas.restore()
    }

    private fun saveMarkerImage(): String? = runCatching {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ar_marker_fantasy_v1)
        val dir = File(context.cacheDir, "easyar_markers")
        dir.mkdirs()
        val file = File(dir, "ar_marker_fantasy_v1.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.i(EASYAR_TAG, "Saved EasyAR marker image path=${file.absolutePath} size=${bitmap.width}x${bitmap.height}")
        file.absolutePath
    }.onFailure { error ->
        Log.e(EASYAR_TAG, "Failed to save marker image", error)
    }.getOrNull()

    private fun disposeAll() {
        running.set(false)
        hasTracking = false
        outputFrameBuffer?.pause()
        outputFrameBuffer?.dispose()
        outputFrameBuffer = null
        feedbackAdapter?.dispose()
        feedbackAdapter = null
        inputFork?.dispose()
        inputFork = null
        imageTracker?.stop()
        imageTracker?.close()
        imageTracker?.dispose()
        imageTracker = null
        cameraDevice?.stop()
        cameraDevice?.close()
        cameraDevice?.dispose()
        cameraDevice = null
        scheduler?.dispose()
        scheduler = null
        trackingTargetId = null
    }
}

private fun Matrix44F.toFloatArray(): FloatArray = data.copyOf()

private fun Matrix44F.toDoubleArray(): DoubleArray = DoubleArray(16) { index -> data[index].toDouble() }

private fun InputFrame.toBitmap(): Bitmap? {
    val frameImage = image()
    return when (frameImage.format()) {
        PixelFormat.YUV_NV21 -> yuvToBitmap(frameImage.width(), frameImage.height(), frameImage.buffer().copyToByteArray())
        PixelFormat.YUV_NV12 -> yuvToBitmap(frameImage.width(), frameImage.height(), nv12ToNv21(frameImage.buffer().copyToByteArray(), frameImage.width(), frameImage.height()))
        PixelFormat.YUV_I420 -> yuvToBitmap(frameImage.width(), frameImage.height(), i420ToNv21(frameImage.buffer().copyToByteArray(), frameImage.width(), frameImage.height()))
        PixelFormat.YUV_YV12 -> yuvToBitmap(frameImage.width(), frameImage.height(), yv12ToNv21(frameImage.buffer().copyToByteArray(), frameImage.width(), frameImage.height()))
        PixelFormat.RGBA8888 -> rgbaToBitmap(frameImage.width(), frameImage.height(), frameImage.buffer().copyToByteArray(), true)
        PixelFormat.BGRA8888 -> rgbaToBitmap(frameImage.width(), frameImage.height(), frameImage.buffer().copyToByteArray(), false)
        else -> null
    }
}

private fun Buffer.copyToByteArray(): ByteArray {
    val bytes = ByteArray(size())
    copyToByteArray(bytes)
    return bytes
}

private fun yuvToBitmap(width: Int, height: Int, nv21Bytes: ByteArray): Bitmap? = runCatching {
    val yuvImage = YuvImage(nv21Bytes, ImageFormat.NV21, width, height, null)
    val stream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, stream)
    val jpegBytes = stream.toByteArray()
    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
}.getOrNull()

private fun rgbaToBitmap(width: Int, height: Int, rgbaBytes: ByteArray, rgba: Boolean): Bitmap? = runCatching {
    val pixels = IntArray(width * height)
    var sourceIndex = 0
    for (index in pixels.indices) {
        val r: Int
        val g: Int
        val b: Int
        val a: Int
        if (rgba) {
            r = rgbaBytes[sourceIndex].toInt() and 0xFF
            g = rgbaBytes[sourceIndex + 1].toInt() and 0xFF
            b = rgbaBytes[sourceIndex + 2].toInt() and 0xFF
            a = rgbaBytes[sourceIndex + 3].toInt() and 0xFF
        } else {
            b = rgbaBytes[sourceIndex].toInt() and 0xFF
            g = rgbaBytes[sourceIndex + 1].toInt() and 0xFF
            r = rgbaBytes[sourceIndex + 2].toInt() and 0xFF
            a = rgbaBytes[sourceIndex + 3].toInt() and 0xFF
        }
        pixels[index] = (a shl 24) or (r shl 16) or (g shl 8) or b
        sourceIndex += 4
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}.getOrNull()

private fun nv12ToNv21(bytes: ByteArray, width: Int, height: Int): ByteArray {
    val frameSize = width * height
    val result = bytes.copyOf()
    var offset = frameSize
    while (offset + 1 < result.size) {
        val u = result[offset]
        result[offset] = result[offset + 1]
        result[offset + 1] = u
        offset += 2
    }
    return result
}

private fun i420ToNv21(bytes: ByteArray, width: Int, height: Int): ByteArray {
    val frameSize = width * height
    val quarter = frameSize / 4
    val result = ByteArray(frameSize + quarter * 2)
    System.arraycopy(bytes, 0, result, 0, frameSize)
    val uOffset = frameSize
    val vOffset = frameSize + quarter
    var dest = frameSize
    repeat(quarter) { index ->
        result[dest++] = bytes[vOffset + index]
        result[dest++] = bytes[uOffset + index]
    }
    return result
}

private fun yv12ToNv21(bytes: ByteArray, width: Int, height: Int): ByteArray {
    val frameSize = width * height
    val quarter = frameSize / 4
    val result = ByteArray(frameSize + quarter * 2)
    System.arraycopy(bytes, 0, result, 0, frameSize)
    val vOffset = frameSize
    val uOffset = frameSize + quarter
    var dest = frameSize
    repeat(quarter) { index ->
        result[dest++] = bytes[vOffset + index]
        result[dest++] = bytes[uOffset + index]
    }
    return result
}
