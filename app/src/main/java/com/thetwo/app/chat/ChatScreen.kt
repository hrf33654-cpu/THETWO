package com.thetwo.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thetwo.app.network.RemoteChatMode
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.CompanionBadge
import com.thetwo.app.ui.MetadataLine
import com.thetwo.app.ui.theme.Aurora
import com.thetwo.app.ui.theme.Ink
import com.thetwo.app.ui.theme.Mist
import com.thetwo.app.ui.theme.NightOutline
import com.thetwo.app.ui.theme.RoseMist

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionViewModel: AppSessionViewModel,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
    onProfileRequired: () -> Unit = {},
    onUnauthorized: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState = viewModel.uiState
    val sessionState = sessionViewModel.uiState
    val companionName = sessionState.companionProfile?.nickname ?: "灵儿"
    val sessionToken = sessionState.authSession?.sessionToken

    LaunchedEffect(sessionState.authSession?.sessionToken) {
        viewModel.bootstrapSession(
            authSession = sessionState.authSession,
            onProfileLoaded = sessionViewModel::setCompanionProfile,
            onProfileRequired = onProfileRequired,
            onRecentCaptureLoaded = { capture ->
                if (capture != null) {
                    sessionViewModel.setRecentCapture(capture)
                } else {
                    sessionViewModel.clearRecentCapture()
                }
            },
            onUnauthorized = onUnauthorized,
        )
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .imePadding(),
        ) {
            ChatHeader(
                companionName = companionName,
                isRestrictedMode = uiState.isRestrictedMode,
                onOpenSummon = onOpenSummon,
                onOpenSettings = onOpenSettings,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                MessagesPanel(
                    messages = uiState.messages,
                    companionName = companionName,
                    isInitializing = uiState.isInitializing,
                    onRetry = { messageId ->
                        viewModel.retryMessage(
                            messageId = messageId,
                            authSession = sessionState.authSession,
                            onUnauthorized = onUnauthorized,
                        )
                    },
                )
            }

            when {
                sessionToken.isNullOrBlank() -> InlineNotice(
                    title = "登录状态已失效",
                    message = "请重新登录后再继续聊天和同步最近作品。",
                    tone = AppChipTone.Danger,
                )

                uiState.errorMessage != null -> InlineNotice(
                    title = "需要处理一下",
                    message = uiState.errorMessage,
                    tone = AppChipTone.Warning,
                )
            }

            sessionState.recentCaptureReference?.let { capture ->
                RecentCaptureCard(capture = capture)
            } ?: run {
                if (uiState.messages.size >= 3) {
                    SummonCtaBanner(
                        companionName = companionName,
                        onOpenSummon = onOpenSummon,
                    )
                }
            }

            ComposerBar(
                draft = uiState.draft,
                companionName = companionName,
                isReplying = uiState.isReplying,
                isInitializing = uiState.isInitializing,
                canSend = !sessionToken.isNullOrBlank() && uiState.draft.isNotBlank(),
                onDraftChange = viewModel::updateDraft,
                onSummon = onOpenSummon,
                onSend = {
                    viewModel.sendMessage(
                        authSession = sessionState.authSession,
                        onUnauthorized = onUnauthorized,
                    )
                },
            )
        }
    }
}

@Composable
private fun ChatHeader(
    companionName: String,
    isRestrictedMode: Boolean,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompanionBadge(name = companionName, size = 56.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = companionName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isRestrictedMode) Color(0xFFFFB449) else Color(0xFF55D66B),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        text = if (isRestrictedMode) "谨慎模式中" else "在线，等着你呢",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mist,
                    )
                }
            }
            SummonButton(onClick = onOpenSummon)
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "打开设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummonButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(listOf(Aurora, RoseMist)),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "召唤",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun InlineNotice(
    title: String,
    message: String,
    tone: AppChipTone,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppPill(text = title, tone = tone)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RecentCaptureCard(capture: RecentCaptureReference) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = Color(0xFFFFFBFD),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        Brush.linearGradient(listOf(RoseMist, Aurora)),
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "最近的召唤作品",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
                Text(
                    text = capture.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = capture.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = Color(0xFFF4F1F5),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Mist,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummonCtaBanner(
    companionName: String,
    onOpenSummon: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onOpenSummon),
        color = Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFF2C7DC), RoundedCornerShape(22.dp))
                .background(
                    Color(0x1FEA72C2),
                    RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "把${companionName}召唤到你的世界里",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = RoseMist,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MessagesPanel(
    messages: List<ChatMessage>,
    companionName: String,
    isInitializing: Boolean,
    onRetry: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 6.dp),
    ) {
        if (isInitializing) {
            item {
                AppPill(
                    text = "正在恢复最近聊天和陪伴状态…",
                    tone = AppChipTone.Default,
                )
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageRow(
                companionName = companionName,
                message = message,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun MessageRow(
    companionName: String,
    message: ChatMessage,
    onRetry: (String) -> Unit,
) {
    val isUser = message.author == MessageAuthor.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            CompanionBadge(
                name = companionName,
                size = 34.dp,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 292.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isUser) {
                Text(
                    text = companionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 22.dp,
                    topEnd = 22.dp,
                    bottomStart = if (isUser) 22.dp else 10.dp,
                    bottomEnd = if (isUser) 10.dp else 22.dp,
                ),
                color = if (isUser) {
                    Brush.linearGradient(listOf(Color(0xFFE95ECA), Color(0xFFF472B6))).toSurfaceColor()
                } else {
                    Color.White
                },
                tonalElevation = 0.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.border(
                    1.dp,
                    if (isUser) {
                        Color.Transparent
                    } else {
                        NightOutline.copy(alpha = 0.6f)
                    },
                    RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = if (isUser) 22.dp else 10.dp,
                        bottomEnd = if (isUser) 10.dp else 22.dp,
                    ),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    when (message.status) {
                        MessageStatus.SENDING -> Text(
                            text = "发送中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUser) Color.White.copy(alpha = 0.78f) else Mist,
                        )

                        MessageStatus.FAILED -> Text(
                            text = "发送失败，点我重试",
                            modifier = Modifier.clickable { onRetry(message.id) },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.error,
                        )

                        MessageStatus.SENT -> {
                            if (!isUser && message.mode != RemoteChatMode.NORMAL) {
                                AppPill(text = "保护模式", tone = AppChipTone.Warning)
                            }
                        }
                    }
                }
            }
            Text(
                text = if (isUser) "刚刚" else "在线陪伴中",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComposerBar(
    draft: String,
    companionName: String,
    isReplying: Boolean,
    isInitializing: Boolean,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSummon: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color.Transparent,
                onClick = onSummon,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFFE0F0), Color(0xFFF1E0FF))),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "打开召唤",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (isReplying) "${companionName} 正在回复…" else "和${companionName}说点什么…")
                },
                enabled = !isInitializing,
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFFF7F7FA),
                    unfocusedContainerColor = Color(0xFFF7F7FA),
                    disabledContainerColor = Color(0xFFF7F7FA),
                ),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend && !isReplying && !isInitializing,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "发送",
                )
            }
        }
    }
}

private fun Brush.toSurfaceColor(): Color = Color(0xFFE95ECA)
