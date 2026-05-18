package com.thetwo.app.summon

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.network.AuthSession
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.GlassPanel
import com.thetwo.app.ui.MetadataLine
import com.thetwo.app.ui.theme.NightOutline
import java.io.File

@Composable
fun CapturePreviewScreen(
    viewModel: SummonViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    authSession: AuthSession?,
    onBack: () -> Unit,
    onBackToChat: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    val uiState = viewModel.uiState
    val previewState = uiState.capturePreviewState
    val context = LocalContext.current
    val companionName = sessionViewModel.uiState.companionProfile?.nickname ?: "灵儿"
    var localFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = previewState?.imageLocation) {
        value = previewState?.let { loadPreviewBitmap(context, it.imageLocation) }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                AppPill(
                    text = if (previewState?.isSyncedToChat == true) "已回流到聊天" else "作品预览",
                    tone = if (previewState?.isSyncedToChat == true) AppChipTone.Success else AppChipTone.Accent,
                )
            }

            if (previewState == null) {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "还没有可预览的作品",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "先去召唤页保存一张截图，这里会展示成片并回流到聊天。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("返回召唤页")
                    }
                }
            } else {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = previewState.recentCaptureReference.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = previewState.recentCaptureReference.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MetadataLine(
                        label = "保存位置",
                        value = previewState.recentCaptureReference.storageLocation,
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "召唤作品预览",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        } else {
                            Column(
                                modifier = Modifier.padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                                Text(
                                    text = "正在读取作品预览…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                uiState.statusMessage?.let { message ->
                    AppPill(
                        text = message,
                        tone = if (previewState.isSyncedToChat) AppChipTone.Success else AppChipTone.Warning,
                    )
                }

                localFeedback?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Warning)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            shareCapture(context, previewState.imageLocation)
                                .onFailure {
                                    localFeedback = "当前设备暂不支持直接分享这张本地作品。"
                                }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = null,
                        )
                        Text("分享", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = onBackToChat,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("回到聊天")
                    }
                }

                if (!previewState.isSyncedToChat) {
                    OutlinedButton(
                        onClick = {
                            val pendingReference = uiState.pendingRecentCapture ?: previewState.recentCaptureReference
                            viewModel.syncRecentCapture(
                                authSession = authSession,
                                reference = pendingReference,
                                onSuccess = { savedReference ->
                                    sessionViewModel.setRecentCapture(savedReference)
                                    chatViewModel.onRecentCaptureRecorded(savedReference, companionName)
                                    viewModel.markCapturePreviewSynced(savedReference)
                                    localFeedback = "作品已经同步回聊天。"
                                },
                                onUnauthorized = onUnauthorized,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSavingCapture,
                    ) {
                        Text(if (uiState.isSavingCapture) "同步中…" else "同步到聊天")
                    }
                }
            }
        }
    }
}

private fun loadPreviewBitmap(
    context: android.content.Context,
    imageLocation: String,
): Bitmap? {
    return runCatching {
        if (imageLocation.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(imageLocation))?.use(BitmapFactory::decodeStream)
        } else {
            File(imageLocation).takeIf { it.exists() }?.inputStream()?.use(BitmapFactory::decodeStream)
        }
    }.getOrNull()
}

private fun shareCapture(
    context: android.content.Context,
    imageLocation: String,
): Result<Unit> {
    return runCatching {
        val uri = when {
            imageLocation.startsWith("content://") -> Uri.parse(imageLocation)
            else -> error("Unsupported share uri")
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享作品"))
    }
}
