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
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.chat.RecentCaptureReference
import com.thetwo.app.media.BitmapSaver
import com.thetwo.app.network.toUserFacingMessage
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.HighlightPanel
import com.thetwo.app.ui.MetadataLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun SummonExperienceScreen(
    viewModel: SummonViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenCapturePreview: () -> Unit,
    onUnauthorized: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }
    val sessionState by remember { androidx.compose.runtime.derivedStateOf { sessionViewModel.uiState } }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val companionName = sessionState.companionProfile?.nickname ?: "灵儿"
    val easyArEngine = remember { ReflectionEasyArEngine() }
    val characterAssetManifest = remember(context) { CharacterAssetSelector.resolve(context) }
    var hasCameraPermission by remember { mutableStateOf(isCameraPermissionGranted(context)) }
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
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
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
            viewModel.setStatusMessage("已获得相机权限，预览模式已经就绪。")
        } else {
            hasCameraPermission = false
            viewModel.showScreenOnlyFallback()
            viewModel.setStatusMessage("未获得相机权限，已切换到仅屏幕回退模式。")
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
            viewModel.setStatusMessage("已识别锚点图，召唤对象正在进入当前画面。")
        }
        easyArSession.onTrackingUpdated = { markerPose ->
            viewModel.setEasyArTrackingUpdated(markerPose)
        }
        easyArSession.onTrackingLost = {
            viewModel.setEasyArTrackingLost()
            viewModel.setStatusMessage("标记丢失了，请重新对准官方识别图。")
        }
        easyArSession.onError = { message ->
            viewModel.setEasyArTrackingFailed(message)
            if (hasCameraPermission) {
                viewModel.showCameraPreviewFallbackAfterEasyArFailure()
            } else {
                viewModel.showScreenOnlyFallbackAfterEasyArFailure()
            }
            viewModel.setStatusMessage("$message，已切回稳定的召唤回退模式。")
        }
    }

    val isShowing3dPreview =
        (uiState.characterModelState == CharacterModelState.LOADING || uiState.characterModelState == CharacterModelState.READY) &&
            uiState.entryState != SummonEntryState.EASYAR_TRACKING
    val isPlaced =
        uiState.characterModelState == CharacterModelState.READY ||
            uiState.characterModelState == CharacterModelState.FALLBACK_2D ||
            uiState.easyArTrackingState == EasyArTrackingState.TRACKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(bottom = contentPadding.calculateBottomPadding()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { stageSize = it },
        ) {
            StageBackdrop(entryState = uiState.entryState)

            when {
                !isShowing3dPreview && uiState.entryState == SummonEntryState.CAMERA_PREVIEW_FALLBACK -> {
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
                }

                !isShowing3dPreview && uiState.entryState == SummonEntryState.EASYAR_TRACKING -> {
                    AndroidView(
                        factory = { viewContext ->
                            TextureView(viewContext).also { tv ->
                                tv.isOpaque = true
                                easyArSession.bindTextureView(tv)
                                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        surface: android.graphics.SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        if (!easyArStarted) {
                                            easyArStarted = easyArSession.start(width, height)
                                        }
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        surface: android.graphics.SurfaceTexture,
                                        width: Int,
                                        height: Int,
                                    ) = Unit

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
                }

                else -> Unit
            }

            if (uiState.entryState == SummonEntryState.EASYAR_TRACKING && uiState.easyArTrackingState != EasyArTrackingState.TRACKING) {
                ScanningOverlay()
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

            if (uiState.characterModelState == CharacterModelState.FALLBACK_2D || uiState.characterModelState == CharacterModelState.FAILED) {
                SimpleCompanionOverlayCard(
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
                CharacterModelState.LOADING -> StageInfoCard(
                    title = "正在加载 3D 预览",
                    body = if (uiState.characterAssetManifest.mobileReady) {
                        "正在准备当前可用于移动端的候选模型。即使加载失败，截图和回退模式仍然可用。"
                    } else {
                        "正在准备占位验证模型。即使最终 3D 资源还没就绪，召唤和截图流程也可以先走通。"
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                CharacterModelState.READY -> StageInfoCard(
                    title = "3D 预览已就绪",
                    body = if (uiState.characterAssetManifest.mobileReady) {
                        "当前移动端候选模型已经加载完成，现在可以通过标记识别把它放进画面。"
                    } else {
                        "占位验证模型已经就绪，可以先体验召唤和截图流程。"
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 108.dp, end = 16.dp),
                    compact = true,
                )

                CharacterModelState.FAILED -> StageFailureCard(
                    message = uiState.characterModelErrorMessage ?: "当前 3D 资源加载失败，建议先切到稳定的 2D 回退模式。",
                    onRetry = { viewModel.retryCharacterModelLoad(sessionState.authSession) },
                    onUse2dFallback = { viewModel.use2dCharacterFallback(sessionState.authSession) },
                    modifier = Modifier.align(Alignment.Center),
                )

                CharacterModelState.FALLBACK_2D -> StageInfoCard(
                    title = "已切换到 2D 回退模式",
                    body = "当前使用的是稳定的 2D 召唤模式，AR 或最终 3D 资源暂时不会阻塞体验。",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 108.dp, end = 16.dp),
                    compact = true,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayCircleButton(
                        icon = Icons.Rounded.ArrowBack,
                        contentDescription = "返回聊天",
                        onClick = onBack,
                    )
                    StatusBadge(
                        label = stageStatusLabel(uiState.entryState, uiState.easyArTrackingState),
                        tone = availabilityTone(uiState.easyArAvailability),
                    )
                    OverlayCircleButton(
                        icon = Icons.Rounded.Image,
                        contentDescription = "查看锚点图",
                        onClick = { showDiagnostics = !showDiagnostics },
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = summonModeLabel(uiState.entryState, uiState.characterModelState),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        Text(
                            text = stageGuidanceText(
                                isShowing3dPreview = isShowing3dPreview,
                                entryState = uiState.entryState,
                                easyArTrackingState = uiState.easyArTrackingState,
                                trackingErrorMessage = uiState.trackingErrorMessage,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            if (isPlaced) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OverlayCircleButton(
                        icon = Icons.Rounded.ZoomIn,
                        contentDescription = "放大",
                        onClick = { scale = (scale + 0.12f).coerceAtMost(2.2f) },
                    )
                    OverlayCircleButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "缩小",
                        onClick = { scale = (scale - 0.12f).coerceAtLeast(0.75f) },
                    )
                    OverlayCircleButton(
                        icon = Icons.Rounded.RotateRight,
                        contentDescription = "旋转",
                        onClick = { rotationYDegrees += 12f },
                    )
                    OverlayCircleButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "重置",
                        onClick = {
                            offsetX = 0f
                            offsetY = 0f
                            scale = 1f
                            rotationYDegrees = 0f
                        },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.statusMessage?.let { status ->
                StatusBadge(
                    label = status,
                    tone = AppChipTone.Default,
                )
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
                        Text("启用相机")
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::showScreenOnlyFallback,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("仅屏幕")
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
                        Text("相机预览")
                    }
                }
            }

            when (uiState.characterModelState) {
                CharacterModelState.READY -> OutlinedButton(
                    onClick = { viewModel.use2dCharacterFallback(sessionState.authSession) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("改用 2D 回退模式")
                }

                CharacterModelState.FALLBACK_2D -> OutlinedButton(
                    onClick = { viewModel.retryCharacterModelLoad(sessionState.authSession) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试 3D 预览")
                }

                else -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomMiniAction(
                    label = "返回",
                    icon = Icons.Rounded.ArrowBack,
                    onClick = onBack,
                )

                CaptureButton(
                    enabled = !uiState.isSavingCapture &&
                        sessionState.arPrivacyAccepted &&
                        uiState.characterModelState != CharacterModelState.LOADING,
                    isSaving = uiState.isSavingCapture,
                    onClick = {
                        scope.launch {
                            viewModel.setSavingCapture(true)
                            viewModel.setStatusMessage(null)
                            val bitmap = createStageCaptureBitmap(
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
                                        title = "最近召唤 #$captureCount",
                                        summary = "你把 $companionName 带进了当前场景，并保存了这一刻。",
                                        storageLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            "已保存到系统相册：$location"
                                        } else {
                                            "已保存到应用图片目录：$location"
                                        },
                                    )
                                    viewModel.setCapturePreview(
                                        CapturePreviewState(
                                            imageLocation = location,
                                            recentCaptureReference = reference,
                                        ),
                                    )
                                    viewModel.syncRecentCapture(
                                        authSession = sessionState.authSession,
                                        reference = reference,
                                        onSuccess = { savedReference ->
                                            sessionViewModel.setRecentCapture(savedReference)
                                            chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                            viewModel.markCapturePreviewSynced(savedReference)
                                        },
                                        onUnauthorized = onUnauthorized,
                                    )
                                    onOpenCapturePreview()
                                }
                                .onFailure { error ->
                                    viewModel.setSavingCapture(false)
                                    viewModel.setStatusMessage(error.toUserFacingMessage("召唤截图保存失败，请重试。"))
                                }
                        }
                    },
                )

                BottomMiniAction(
                    label = if (showDiagnostics) "隐藏" else "详情",
                    icon = Icons.Rounded.Image,
                    onClick = { showDiagnostics = !showDiagnostics },
                )
            }

            if (showDiagnostics) {
                HighlightPanel(modifier = Modifier.fillMaxWidth()) {
                    MetadataLine(
                        label = "EasyAR 状态",
                        value = availabilityLabel(uiState.easyArAvailability),
                    )
                    MetadataLine(
                        label = "识别标记",
                        value = "${uiState.markerAsset.name} / ${uiState.markerAsset.resourceName}",
                    )
                    MetadataLine(
                        label = "当前资源",
                        value = if (uiState.characterAssetManifest.mobileReady) {
                            "${uiState.characterAssetManifest.activeGlbName}（移动端候选）"
                        } else {
                            "${uiState.characterAssetManifest.activeGlbName}；原始资源 ${uiState.characterAssetManifest.sourceAssetName} 仍需移动端转换"
                        },
                    )
                    MetadataLine(
                        label = "使用建议",
                        value = uiState.markerAsset.recommendedUsage,
                    )
                    uiState.pendingRecentCapture?.let { pendingReference ->
                        OutlinedButton(
                            onClick = {
                                viewModel.syncRecentCapture(
                                    authSession = sessionState.authSession,
                                    reference = pendingReference,
                                    onSuccess = { savedReference ->
                                        sessionViewModel.setRecentCapture(savedReference)
                                        chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                        viewModel.markCapturePreviewSynced(savedReference)
                                    },
                                    onUnauthorized = onUnauthorized,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSavingCapture,
                        ) {
                            Text("重试同步作品")
                        }
                    }
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
                    Text("继续")
                }
            },
            title = { Text("相机与召唤预览说明") },
            text = {
                Text(
                    "召唤功能会使用相机预览来确认摆放位置，并把静态截图保存到系统相册或图片目录，不会构建环境模型。",
                )
            },
        )
    }
}

@Composable
private fun StageBackdrop(entryState: SummonEntryState) {
    val colors = if (entryState == SummonEntryState.SCREEN_ONLY_FALLBACK) {
        listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
    } else {
        listOf(Color(0xFF0D1117), Color(0xFF1C1E2E), Color(0xFF252836))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x558B5CF6), Color.Transparent),
                        radius = 700f,
                    ),
                ),
        )
    }
}

@Composable
private fun ScanningOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 180.dp)
            .border(2.dp, Color(0xCCB18BFF), RoundedCornerShape(18.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, start = 18.dp, end = 18.dp)
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFFB18BFF), Color.Transparent),
                    ),
                ),
        )
        Text(
            text = "请对准官方锚点图",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD8C7FF),
        )
    }
}

@Composable
private fun OverlayCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.42f),
        tonalElevation = 0.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    tone: AppChipTone,
) {
    val background = when (tone) {
        AppChipTone.Success -> Color(0xFF1FAE66)
        AppChipTone.Warning -> Color(0xFFE6A93A)
        AppChipTone.Danger -> Color(0xFFCE4D68)
        AppChipTone.Accent -> Color(0xFF9F67FF)
        AppChipTone.Default -> Color(0x66000000)
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun BottomMiniAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.18f),
            tonalElevation = 0.dp,
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun CaptureButton(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = if (enabled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                4.dp,
                if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
            ),
            onClick = onClick,
            enabled = enabled && !isSaving,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (enabled) Color.White else Color.White.copy(alpha = 0.36f),
                            CircleShape,
                        ),
                )
            }
        }
        Text(
            text = if (isSaving) "保存中…" else if (enabled) "拍摄" else "等待中",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun SimpleCompanionOverlayCard(
    companionName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(width = 168.dp, height = 220.dp)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp)),
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = companionName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = "已召唤",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Text(
                text = "拖动 / 缩放 / 旋转",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun StageInfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0F172A)),
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = body,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun StageFailureCard(
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
                text = "3D 预览不可用",
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
                    Text("重试 3D")
                }
                OutlinedButton(onClick = onUse2dFallback) {
                    Text("使用 2D 回退")
                }
            }
        }
    }
}

private fun availabilityLabel(availability: EasyArAvailability): String {
    return when (availability) {
        EasyArAvailability.READY_FOR_TRACKING -> "EasyAR 已就绪"
        EasyArAvailability.LICENSE_MISSING -> "缺少授权"
        EasyArAvailability.ARCHIVE_ONLY -> "仅检测到 SDK 压缩包"
        EasyArAvailability.NOT_BUNDLED -> "回退模式"
    }
}

private fun availabilityTone(availability: EasyArAvailability): AppChipTone {
    return when (availability) {
        EasyArAvailability.READY_FOR_TRACKING -> AppChipTone.Success
        EasyArAvailability.LICENSE_MISSING -> AppChipTone.Danger
        EasyArAvailability.ARCHIVE_ONLY -> AppChipTone.Warning
        EasyArAvailability.NOT_BUNDLED -> AppChipTone.Warning
    }
}

private fun stageStatusLabel(
    entryState: SummonEntryState,
    trackingState: EasyArTrackingState,
): String {
    return when {
        entryState == SummonEntryState.EASYAR_TRACKING && trackingState == EasyArTrackingState.TRACKING -> "锚点识别成功"
        entryState == SummonEntryState.EASYAR_TRACKING && trackingState == EasyArTrackingState.LOST -> "标记已丢失"
        entryState == SummonEntryState.EASYAR_TRACKING && trackingState == EasyArTrackingState.FAILED -> "识别异常"
        entryState == SummonEntryState.CAMERA_PERMISSION_REQUIRED -> "需要相机权限"
        entryState == SummonEntryState.SCREEN_ONLY_FALLBACK -> "仅屏幕回退"
        entryState == SummonEntryState.CAMERA_PREVIEW_FALLBACK -> "相机回退已启用"
        else -> "可开始召唤"
    }
}

private fun stageGuidanceText(
    isShowing3dPreview: Boolean,
    entryState: SummonEntryState,
    easyArTrackingState: EasyArTrackingState,
    trackingErrorMessage: String?,
): String {
    return if (isShowing3dPreview) {
        "你可以拖动、缩放和旋转预览模型，先确认大小和构图是否合适。"
    } else if (entryState == SummonEntryState.EASYAR_TRACKING) {
        when (easyArTrackingState) {
            EasyArTrackingState.IDLE -> "请把官方识别图放进画面，开始 AR 追踪。"
            EasyArTrackingState.TRACKING -> "标记识别成功，召唤对象已经附着在锚点上并跟随其姿态移动。"
            EasyArTrackingState.LOST -> "标记丢失了，请重新对准官方识别图，把召唤对象找回来。"
            EasyArTrackingState.FAILED -> trackingErrorMessage ?: "EasyAR 追踪失败，你可以切回 2D 回退模式。"
        }
    } else {
        "在最终 3D 资源就绪前，你可以先用相机模式或仅屏幕回退模式确认画面构图。"
    }
}

private fun summonModeLabel(
    entryState: SummonEntryState,
    modelState: CharacterModelState,
): String {
    val previewLabel = when (entryState) {
        SummonEntryState.PRIVACY_REQUIRED -> "隐私确认"
        SummonEntryState.CAMERA_PERMISSION_REQUIRED -> "等待授权"
        SummonEntryState.CAMERA_PREVIEW_FALLBACK -> "相机预览"
        SummonEntryState.SCREEN_ONLY_FALLBACK -> "仅屏幕"
        SummonEntryState.EASYAR_TRACKING -> "EasyAR 相机"
    }
    val modelLabel = when (modelState) {
        CharacterModelState.LOADING -> "3D 加载中"
        CharacterModelState.READY -> "3D 已就绪"
        CharacterModelState.FAILED -> "3D 加载失败"
        CharacterModelState.FALLBACK_2D -> "2D 回退"
    }
    return "$previewLabel / $modelLabel"
}

private fun isCameraPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

private fun createStageCaptureBitmap(
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

    drawCaptureOverlay(
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

private fun drawCaptureOverlay(
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

    canvas.save()
    canvas.rotate(rotation, centerX, centerY)

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                android.graphics.Color.parseColor("#172038"),
                android.graphics.Color.parseColor("#2F4D78"),
                android.graphics.Color.parseColor("#8BD6FF"),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRoundRect(rect, 42f * scale, 42f * scale, cardPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 56f * scale
        isFakeBoldText = true
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255)
        textSize = 34f * scale
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(210, 255, 255, 255)
        textSize = 26f * scale
    }

    canvas.drawText("THETWO", rect.left + 30f * scale, rect.top + 56f * scale, footerPaint)
    canvas.drawText(companionName, rect.left + 30f * scale, rect.bottom - 92f * scale, titlePaint)
    canvas.drawText("已召唤", rect.left + 30f * scale, rect.bottom - 46f * scale, subtitlePaint)
    canvas.drawText("拖动 / 缩放 / 旋转", rect.left + 30f * scale, rect.bottom - 16f * scale, footerPaint)

    canvas.restore()
}
