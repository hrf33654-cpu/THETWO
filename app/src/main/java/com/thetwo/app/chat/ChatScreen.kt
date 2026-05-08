package com.thetwo.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.session.AppSessionViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionViewModel: AppSessionViewModel,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
    onProfileRequired: () -> Unit = {},
    onUnauthorized: () -> Unit = {},
) {
    val uiState = viewModel.uiState
    val sessionState = sessionViewModel.uiState
    val companionName = sessionState.companionProfile?.nickname ?: "角色"
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
            onUnauthorized = {
                sessionViewModel.clearAuthenticatedState()
                onUnauthorized()
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 16.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            ChatHeader(
                companionName = companionName,
                email = sessionState.authSession?.email,
                onOpenSummon = onOpenSummon,
                onOpenSettings = onOpenSettings,
            )

            if (sessionToken.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                InlineNotice(
                    text = "当前未登录，聊天不会同步到后端。请先重新登录后再继续联调。",
                    isError = true,
                )
            }

            if (uiState.isRestrictedMode) {
                Spacer(modifier = Modifier.height(12.dp))
                InlineNotice(
                    text = "当前会话已切到更保守的陪伴模式，回复会收紧，也不会主动发起召唤邀约。",
                    isError = true,
                )
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                InlineNotice(
                    text = message,
                    isError = true,
                )
            }

            sessionState.recentCaptureReference?.let { capture ->
                Spacer(modifier = Modifier.height(12.dp))
                RecentCaptureCard(capture = capture)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.isInitializing) {
                Text(
                    text = "正在同步聊天记录…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onRetry = if (message.status == MessageStatus.FAILED) {
                            {
                                viewModel.retryMessage(
                                    messageId = message.id,
                                    authSession = sessionState.authSession,
                                    onUnauthorized = {
                                        sessionViewModel.clearAuthenticatedState()
                                        onUnauthorized()
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextField(
                        value = uiState.draft,
                        onValueChange = viewModel::updateDraft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        placeholder = {
                            Text("给 $companionName 发一条消息")
                        },
                        minLines = 3,
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.sendMessage(
                                authSession = sessionState.authSession,
                                onUnauthorized = {
                                    sessionViewModel.clearAuthenticatedState()
                                    onUnauthorized()
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isReplying && !uiState.isInitializing && !sessionToken.isNullOrBlank(),
                    ) {
                        Text(if (uiState.isReplying) "发送中…" else "发送")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ChatHeader(
    companionName: String,
    email: String?,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Text(
                text = "THETWO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = companionName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "聊天首页",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            email?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenSummon,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("召唤角色")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("设置")
                }
            }
        }
    }
}

@Composable
private fun InlineNotice(
    text: String,
    isError: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun RecentCaptureCard(capture: RecentCaptureReference) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = capture.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = capture.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = capture.storageLocation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: (() -> Unit)?,
) {
    val isUser = message.author == MessageAuthor.USER

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = if (isUser) 24.dp else 8.dp,
                bottomEnd = if (isUser) 8.dp else 24.dp,
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            },
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isUser) "你" else "角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                )
                when (message.status) {
                    MessageStatus.SENDING -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "发送中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    MessageStatus.FAILED -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "发送失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        onRetry?.let {
                            TextButton(
                                onClick = it,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("重试发送")
                            }
                        }
                    }

                    MessageStatus.SENT -> Unit
                }
            }
        }
    }
}
