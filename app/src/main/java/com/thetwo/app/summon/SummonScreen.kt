package com.thetwo.app.summon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thetwo.app.R
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.media.BitmapSaver
import com.thetwo.app.network.toUserFacingMessage
import com.thetwo.app.session.AppSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SummonScreen(
    viewModel: SummonViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val uiState by remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }
    val sessionState by remember { androidx.compose.runtime.derivedStateOf { sessionViewModel.uiState } }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val companionName = sessionState.companionProfile?.nickname ?: "THETWO"
    val easyArEngine = remember { ReflectionEasyArEngine() }
    val characterAssetManifest = remember(context) { CharacterAssetSelector.resolve(context) }
    var hasCameraPermission by remember {
        mutableStateOf(isCameraPermissionGranted(context))
    }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetX by rememberSaveable { mutableStateOf(0f) }
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var rotationYDegrees by rememberSaveable { mutableStateOf(0f) }
    var captureCount by rememberSaveable { mutableIntStateOf(0) }
    val transformState = CharacterTransformState(
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotationYDegrees = rotationYDegrees,
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            hasCameraPermission = true
            viewModel.showCameraPreviewFallback()
            viewModel.setStatusMessage("Camera permission granted. Preview fallback is ready.")
        } else {
            hasCameraPermission = false
            viewModel.showScreenOnlyFallback()
            viewModel.setStatusMessage("Camera permission denied. Using screen-only fallback.")
        }
    }

    val easyArAvailability = remember {
        when {
            EasyArSdkLocator.hasArchiveOnly() && !easyArEngine.isRuntimeAvailable() -> EasyArAvailability.ARCHIVE_ONLY
            !easyArEngine.isRuntimeAvailable() -> EasyArAvailability.NOT_BUNDLED
            !EasyArInitializer.hasLicenseKey() -> EasyArAvailability.LICENSE_MISSING
            easyArEngine.canStartImageTracking() -> EasyArAvailability.READY_FOR_TRACKING
            else -> EasyArAvailability.NOT_BUNDLED
        }
    }

    val easyArSession = remember { EasyArSession(context) }
    var easyArTextureView by remember { mutableStateOf<TextureView?>(null) }
    var easyArStarted by remember { mutableStateOf(false) }

    LaunchedEffect(characterAssetManifest) {
        viewModel.setCharacterAssetManifest(characterAssetManifest)
    }

    DisposableEffect(lifecycleOwner, hasCameraPermission) {
        if (hasCameraPermission) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.unbind()
            easyArSession.stop()
            easyArStarted = false
        }
    }

    LaunchedEffect(sessionState.arPrivacyAccepted, hasCameraPermission, easyArAvailability) {
        viewModel.setEasyArAvailability(easyArAvailability)
        when {
            !sessionState.arPrivacyAccepted -> viewModel.requirePrivacyAck()
            easyArAvailability == EasyArAvailability.READY_FOR_TRACKING && hasCameraPermission -> viewModel.showEasyArTracking()
            hasCameraPermission -> viewModel.showCameraPreviewFallback()
            else -> viewModel.showCameraPermissionRequired()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.trackSummonOpened(sessionState.authSession)
    }

    LaunchedEffect(easyArSession) {
        easyArSession.onTrackingStarted = { markerPose ->
            viewModel.setEasyArTrackingStarted(markerPose)
            viewModel.setStatusMessage("Marker tracked. Summon placed into camera view.")
        }
        easyArSession.onTrackingUpdated = { markerPose ->
            viewModel.setEasyArTrackingUpdated(markerPose)
        }
        easyArSession.onTrackingLost = {
            viewModel.setEasyArTrackingLost()
            viewModel.setStatusMessage("Marker lost. Re-align the official marker.")
        }
        easyArSession.onError = { message ->
            viewModel.setEasyArTrackingFailed(message)
            if (hasCameraPermission) {
                viewModel.showCameraPreviewFallbackAfterEasyArFailure()
            } else {
                viewModel.showScreenOnlyFallbackAfterEasyArFailure()
            }
            viewModel.setStatusMessage(
                "$message Switched back to stable fallback summon mode.",
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Summon",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (uiState.characterAssetManifest.mobileReady) {
                    "This stage uses the current mobile-ready 3D candidate for runtime validation. If tracking or loading fails, the summon page must stay on stable fallback."
                } else {
                    "This stage uses a placeholder 3D cube to validate EasyAR image tracking. The cube should appear only after the official marker is recognized inside the camera view."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (uiState.easyArAvailability) {
                    EasyArAvailability.READY_FOR_TRACKING -> "EasyAR runtime: available"
                    EasyArAvailability.LICENSE_MISSING -> "EasyAR runtime: bundled, but license key is missing"
                    EasyArAvailability.ARCHIVE_ONLY -> "EasyAR SDK archive detected. Extract EasyAR.aar into app/libs to enable runtime loading."
                    EasyArAvailability.NOT_BUNDLED -> "EasyAR runtime: not bundled, using fallback"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Marker: ${uiState.markerAsset.name} / ${uiState.markerAsset.resourceName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Model: assets/models/${uiState.characterAssetManifest.expectedGlbName}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (uiState.characterAssetManifest.mobileReady) {
                    "Active 3D asset: ${uiState.characterAssetManifest.activeGlbName} (mobile-ready candidate)"
                } else {
                    "Active 3D asset: ${uiState.characterAssetManifest.activeGlbName}; source asset ${uiState.characterAssetManifest.sourceAssetName} is still pending mobile conversion"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF121629), Color(0xFF25304F), Color(0xFF4D759B)),
                        ),
                    )
                    .onSizeChanged { stageSize = it },
            ) {
                val isShowing3dPreview =
                    (uiState.characterModelState == CharacterModelState.LOADING ||
                        uiState.characterModelState == CharacterModelState.READY) &&
                        uiState.entryState != SummonEntryState.EASYAR_TRACKING

                if (!isShowing3dPreview && uiState.entryState == SummonEntryState.CAMERA_PREVIEW_FALLBACK) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                controller = cameraController
                            }.also { previewView = it }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.controller = cameraController
                            previewView = view
                        },
                    )
                } else if (!isShowing3dPreview && uiState.entryState == SummonEntryState.EASYAR_TRACKING) {
                    AndroidView(
                        factory = { viewContext ->
                            TextureView(viewContext).also { tv ->
                                tv.isOpaque = true
                                easyArTextureView = tv
                                easyArSession.bindTextureView(tv)
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                        if (!easyArStarted) {
                                            easyArStarted = easyArSession.start(width, height)
                                        }
                                    }

                                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit

                                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                        easyArSession.stop()
                                        easyArStarted = false
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SummonStageBackdrop(isShowing3dPreview = isShowing3dPreview)
                }

                if (uiState.characterModelState == CharacterModelState.LOADING || uiState.characterModelState == CharacterModelState.READY) {
                    CharacterModelViewport(
                        modifier = Modifier.fillMaxSize(),
                        assetPath = "models/${uiState.characterAssetManifest.activeGlbName}",
                        loadAttempt = uiState.characterModelLoadAttempt,
                        transformState = transformState,
                        markerPoseState = uiState.markerPoseState,
                        onLoadStarted = {
                            viewModel.markCharacterModelLoadStarted(sessionState.authSession)
                        },
                        onLoadSucceeded = {
                            viewModel.markCharacterModelLoadSucceeded(sessionState.authSession)
                        },
                        onLoadFailed = { errorCode, message ->
                            viewModel.markCharacterModelLoadFailed(
                                authSession = sessionState.authSession,
                                errorCode = errorCode,
                                message = message,
                            )
                        },
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.ar_marker_fantasy_v1),
                    contentDescription = "Official marker",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )

                if (uiState.characterModelState == CharacterModelState.FALLBACK_2D || uiState.characterModelState == CharacterModelState.FAILED) {
                    CompanionOverlayCard(
                        companionName = companionName,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotationYDegrees,
                            )
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotationChange ->
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    scale = (scale * zoom).coerceIn(0.75f, 2.2f)
                                    rotationYDegrees += rotationChange
                                }
                            },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotationChange ->
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    scale = (scale * zoom).coerceIn(0.75f, 2.2f)
                                    rotationYDegrees += rotationChange
                                }
                            },
                    )
                }

                when (uiState.characterModelState) {
                    CharacterModelState.LOADING -> {
                        ModelStatusCard(
                            title = "Loading 3D character",
                            body = if (uiState.characterAssetManifest.mobileReady) {
                                "The summon stage is preparing the bundled mobile-ready character candidate. Existing screenshot and fallback flows remain available after load fails."
                            } else {
                                "The summon stage is preparing the placeholder 3D cube. Existing screenshot and fallback flows remain available after load fails."
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    CharacterModelState.READY -> {
                        ModelStatusCard(
                            title = "3D preview ready",
                            body = if (uiState.characterAssetManifest.mobileReady) {
                                "The mobile-ready 3D candidate loaded successfully. Waiting for marker tracking or fallback validation. Saved screenshots still use the current fallback capture path."
                            } else {
                                "Placeholder 3D asset ready. Waiting for marker tracking to place the summon into camera view. Saved screenshots still use the current fallback capture path."
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            compact = true,
                        )
                    }

                    CharacterModelState.FAILED -> {
                        FailedModelCard(
                            message = uiState.characterModelErrorMessage ?: if (uiState.characterAssetManifest.mobileReady) {
                                "Failed to load the current mobile-ready 3D candidate. You can keep using 2D fallback while the asset is adjusted."
                            } else {
                                "Failed to load the active 3D preview asset. character.glb is still not considered mobile-ready, so the stable path remains the placeholder or 2D fallback."
                            },
                            onRetry = {
                                viewModel.retryCharacterModelLoad(sessionState.authSession)
                            },
                            onUse2dFallback = {
                                viewModel.use2dCharacterFallback(sessionState.authSession)
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    CharacterModelState.FALLBACK_2D -> {
                        ModelStatusCard(
                            title = "2D fallback active",
                            body = "You are using the stable 2D summon fallback with camera or screen background. Screenshot and recent capture sync stay unchanged while EasyAR and final 3D assets remain non-blocking.",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            compact = true,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                ) {
                    Text(
                        text = summonModeLabel(uiState.entryState, uiState.characterModelState),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isShowing3dPreview) {
                            "Drag, scale, and rotate the 3D summon. Rotation now targets the model's Y axis."
                        } else if (uiState.entryState == SummonEntryState.EASYAR_TRACKING) {
                            when (uiState.easyArTrackingState) {
                                EasyArTrackingState.IDLE -> "Place the official marker inside the summon stage to start AR tracking."
                                EasyArTrackingState.TRACKING -> "Marker tracked. The cube is now attached to the marker and follows its pose."
                                EasyArTrackingState.LOST -> "Marker lost. Re-align the official marker to bring the cube back."
                                EasyArTrackingState.FAILED -> uiState.trackingErrorMessage ?: "EasyAR tracking failed. You can switch back to the 2D fallback."
                            }
                        } else {
                            "Drag, scale, and rotate the summon fallback. Rotation now targets the preview model's Y axis."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            uiState.statusMessage?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.entryState == SummonEntryState.CAMERA_PERMISSION_REQUIRED || uiState.entryState == SummonEntryState.SCREEN_ONLY_FALLBACK) {
                    OutlinedButton(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Request camera")
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::showScreenOnlyFallback,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Screen-only")
                    }
                    OutlinedButton(
                        onClick = {
                            if (hasCameraPermission) {
                                viewModel.showCameraPreviewFallback()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Camera preview")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.characterModelState == CharacterModelState.READY) {
                OutlinedButton(
                    onClick = { viewModel.use2dCharacterFallback(sessionState.authSession) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use 2D fallback instead")
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (uiState.characterModelState == CharacterModelState.FALLBACK_2D) {
                OutlinedButton(
                    onClick = { viewModel.retryCharacterModelLoad(sessionState.authSession) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Retry 3D preview")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.setSavingCapture(true)
                            viewModel.setStatusMessage(null)
                            val bitmap = createCaptureBitmap(
                                previewBitmap = previewView?.bitmap,
                                stageSize = stageSize,
                                companionName = companionName,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = scale,
                                rotation = rotationYDegrees,
                            )
                            val saveResult = withContext(Dispatchers.IO) {
                                BitmapSaver.saveCapture(context, bitmap)
                            }
                            saveResult
                                .onSuccess { location ->
                                    viewModel.trackLocalCaptureSaved(
                                        authSession = sessionState.authSession,
                                    )
                                    captureCount += 1
                                    val reference = RecentCaptureReference(
                                        title = "Recent summon #$captureCount",
                                        summary = "You placed $companionName into a summon scene and saved the current frame.",
                                        storageLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            "Saved to the system gallery: $location"
                                        } else {
                                            "Saved to the app pictures directory: $location"
                                        },
                                    )
                                    viewModel.syncRecentCapture(
                                        authSession = sessionState.authSession,
                                        reference = reference,
                                        onSuccess = { savedReference ->
                                            sessionViewModel.setRecentCapture(savedReference)
                                            chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                            onBack()
                                        },
                                        onUnauthorized = onUnauthorized,
                                    )
                                }
                                .onFailure { error ->
                                    viewModel.setSavingCapture(false)
                                    viewModel.setStatusMessage(error.toUserFacingMessage("保存召唤截图失败，请稍后重试。"))
                                }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSavingCapture &&
                        sessionState.arPrivacyAccepted &&
                        uiState.characterModelState != CharacterModelState.LOADING,
                ) {
                    Text(if (uiState.isSavingCapture) "Saving..." else "Save summon capture")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back to chat")
                }
            }

            uiState.pendingRecentCapture?.let { pendingReference ->
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.syncRecentCapture(
                            authSession = sessionState.authSession,
                            reference = pendingReference,
                            onSuccess = { savedReference ->
                                sessionViewModel.setRecentCapture(savedReference)
                                chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                onBack()
                            },
                            onUnauthorized = onUnauthorized,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSavingCapture,
                ) {
                    Text("Retry recent capture sync")
                }
            }
        }
    }

    if (!sessionState.arPrivacyAccepted) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(
                    onClick = {
                        sessionViewModel.acceptArPrivacy()
                        if (hasCameraPermission) {
                            viewModel.showCameraPreviewFallback()
                        } else {
                            viewModel.showCameraPermissionRequired()
                        }
                    },
                ) {
                    Text("Continue")
                }
            },
            title = { Text("Camera and summon preview notice") },
            text = {
                Text(
                    "The summon page uses the camera preview to validate summon placement and saves a screenshot to the device gallery or pictures directory. It does not build a room model.",
                )
            },
        )
    }
}

@Composable
private fun CompanionFallbackBackdrop() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF5E7CE2), Color(0xFF121629)),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Screen-only fallback",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Even without camera preview, the summon stage stays available and can still save a fallback capture.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SummonStageBackdrop(
    isShowing3dPreview: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1020),
                        Color(0xFF121A31),
                        Color(0xFF1C2B4A),
                    ),
                ),
            )
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isShowing3dPreview) "3D validation stage" else "Fallback summon stage",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isShowing3dPreview) {
                        "This stage prioritizes visible 3D rendering stability over camera composition."
                    } else {
                        "Camera or screen-only fallback remains available when 3D preview is disabled."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun CompanionOverlayCard(
    companionName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(width = 168.dp, height = 220.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF172038), Color(0xFF2F4D78), Color(0xFF8BD6FF)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "THETWO",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            Column {
                Text(
                    text = companionName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = "Summoned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                text = "Drag / Scale / Rotate",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ModelStatusCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC0F172A),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(if (compact) 12.dp else 16.dp),
        ) {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun FailedModelCard(
    message: String,
    onRetry: () -> Unit,
    onUse2dFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xE61F2937)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "3D preview unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Text("Retry 3D")
                }
                OutlinedButton(onClick = onUse2dFallback) {
                    Text("Use 2D fallback")
                }
            }
        }
    }
}

private fun summonModeLabel(
    entryState: SummonEntryState,
    modelState: CharacterModelState,
): String {
    val previewLabel = when (entryState) {
        SummonEntryState.PRIVACY_REQUIRED -> "Privacy gate"
        SummonEntryState.CAMERA_PERMISSION_REQUIRED -> "Awaiting camera permission"
        SummonEntryState.CAMERA_PREVIEW_FALLBACK -> "Camera preview"
        SummonEntryState.SCREEN_ONLY_FALLBACK -> "Screen-only"
        SummonEntryState.EASYAR_TRACKING -> "EasyAR camera"
    }
    val modelLabel = when (modelState) {
        CharacterModelState.LOADING -> "3D loading"
        CharacterModelState.READY -> "3D ready"
        CharacterModelState.FAILED -> "3D failed"
        CharacterModelState.FALLBACK_2D -> "2D fallback"
    }
    return "$previewLabel / $modelLabel"
}

private fun isCameraPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun isEasyArRuntimeAvailable(): Boolean = runCatching {
    Class.forName("cn.easyar.Engine")
}.isSuccess

private fun createCaptureBitmap(
    previewBitmap: Bitmap?,
    stageSize: IntSize,
    companionName: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotation: Float,
): Bitmap {
    val baseBitmap = previewBitmap?.copy(Bitmap.Config.ARGB_8888, true)
        ?: Bitmap.createBitmap(
            stageSize.width.coerceAtLeast(1080),
            stageSize.height.coerceAtLeast(1440),
            Bitmap.Config.ARGB_8888,
        )

    val canvas = Canvas(baseBitmap)
    if (previewBitmap == null) {
        val backgroundPaint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                baseBitmap.width.toFloat(),
                baseBitmap.height.toFloat(),
                intArrayOf(
                    android.graphics.Color.parseColor("#14182B"),
                    android.graphics.Color.parseColor("#35557A"),
                    android.graphics.Color.parseColor("#6FCFFE"),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, baseBitmap.width.toFloat(), baseBitmap.height.toFloat(), backgroundPaint)
    }

    drawCompanionOverlay(
        canvas = canvas,
        bitmapWidth = baseBitmap.width,
        bitmapHeight = baseBitmap.height,
        stageSize = stageSize,
        companionName = companionName,
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotation = rotation,
    )
    return baseBitmap
}

private fun drawCompanionOverlay(
    canvas: Canvas,
    bitmapWidth: Int,
    bitmapHeight: Int,
    stageSize: IntSize,
    companionName: String,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    rotation: Float,
) {
    val stageWidth = stageSize.width.coerceAtLeast(bitmapWidth)
    val stageHeight = stageSize.height.coerceAtLeast(bitmapHeight)
    val cardWidth = 320f * scale
    val cardHeight = 420f * scale
    val centerX = bitmapWidth / 2f + offsetX * bitmapWidth / stageWidth
    val centerY = bitmapHeight / 2f + offsetY * bitmapHeight / stageHeight
    val rect = RectF(
        centerX - cardWidth / 2f,
        centerY - cardHeight / 2f,
        centerX + cardWidth / 2f,
        centerY + cardHeight / 2f,
    )

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                android.graphics.Color.parseColor("#18233D"),
                android.graphics.Color.parseColor("#38628A"),
                android.graphics.Color.parseColor("#8ADFFF"),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 34f * scale
        isFakeBoldText = true
    }
    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 54f * scale
        isFakeBoldText = true
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255)
        textSize = 28f * scale
    }

    canvas.save()
    canvas.rotate(rotation, centerX, centerY)
    canvas.drawRoundRect(rect, 44f * scale, 44f * scale, cardPaint)
    canvas.drawRoundRect(rect, 44f * scale, 44f * scale, glowPaint)
    canvas.drawText("THETWO", rect.left + 26f * scale, rect.top + 52f * scale, titlePaint)
    canvas.drawText(companionName, rect.left + 26f * scale, rect.centerY(), namePaint)
    canvas.drawText("Summoned", rect.left + 26f * scale, rect.centerY() + 44f * scale, labelPaint)
    canvas.drawText("Captured in reality", rect.left + 26f * scale, rect.bottom - 32f * scale, labelPaint)
    canvas.restore()
}
