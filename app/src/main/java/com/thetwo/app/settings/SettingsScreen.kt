package com.thetwo.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.CompanionBadge
import com.thetwo.app.ui.GlassPanel
import com.thetwo.app.ui.HighlightPanel
import com.thetwo.app.ui.MetadataLine
import com.thetwo.app.ui.theme.NightOutline

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit = {},
    onLogout: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val sessionState = sessionViewModel.uiState
    val uiState = viewModel.uiState
    val companionName = sessionState.companionProfile?.nickname ?: "灵儿"
    var pendingAction by rememberSaveable { mutableStateOf<SettingsActionKind?>(null) }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 18.dp,
                ),
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
                AppPill(text = "设置", tone = AppChipTone.Accent)
            }

            HighlightPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CompanionBadge(name = companionName, size = 62.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = companionName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = sessionState.authSession?.email ?: "当前会话",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MetadataLine(
                    label = "隐私说明",
                    value = "召唤页只会调用相机预览和本地截图保存，不会建立空间模型。",
                )
            }

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "账号与隐私",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                MetadataLine(
                    label = "最近作品",
                    value = sessionState.recentCaptureReference?.title ?: "还没有最近一次作品回流",
                )
                MetadataLine(
                    label = "聊天同步",
                    value = "你可以在这里清理最近作品引用、聊天历史，或直接退出登录。",
                )
            }

            sessionState.recentCaptureReference?.let { capture ->
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    AppPill(text = "最近一次作品", tone = AppChipTone.Accent)
                    Text(
                        text = capture.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = capture.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MetadataLine(label = "保存位置", value = capture.storageLocation)
                }
            }

            SettingsGroup(
                title = "数据管理",
                items = listOf(
                    SettingsActionItem(
                        kind = SettingsActionKind.ClearRecentCapture,
                        label = "清除最近作品回流",
                        description = "只会删除 THETWO 里的回流引用，不会删除系统相册中的原图。",
                        icon = Icons.Rounded.PhotoLibrary,
                        enabled = sessionState.recentCaptureReference != null && !uiState.isWorking,
                    ),
                    SettingsActionItem(
                        kind = SettingsActionKind.ClearChatHistory,
                        label = "清除同步聊天记录",
                        description = "把服务端聊天历史重置到欢迎语起点，本地消息也会一起刷新。",
                        icon = Icons.Rounded.History,
                        enabled = !uiState.isWorking,
                    ),
                ),
                onItemClick = { pendingAction = it.kind },
            )

            SettingsGroup(
                title = "会话管理",
                items = listOf(
                    SettingsActionItem(
                        kind = SettingsActionKind.Logout,
                        label = "退出登录",
                        description = "退出当前账号并回到邮箱验证码登录流程。",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        enabled = !uiState.isWorking,
                    ),
                    SettingsActionItem(
                        kind = SettingsActionKind.DeleteAccount,
                        label = "删除账号数据",
                        description = "删除账号级数据并回到登录页，这个操作无法恢复。",
                        icon = Icons.Rounded.DeleteOutline,
                        enabled = !uiState.isWorking,
                        destructive = true,
                    ),
                ),
                onItemClick = { pendingAction = it.kind },
            )

            uiState.feedbackMessage?.let { message ->
                AppPill(
                    text = message,
                    tone = AppChipTone.Success,
                )
            }
        }
    }

    pendingAction?.let { action ->
        val dialog = action.dialog()
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingAction = null
                        when (action) {
                            SettingsActionKind.ClearRecentCapture -> viewModel.clearRecentCapture(
                                authSession = sessionState.authSession,
                                onSuccess = {
                                    sessionViewModel.clearRecentCapture()
                                    chatViewModel.clearRecentCaptureReference()
                                },
                                onUnauthorized = onUnauthorized,
                            )

                            SettingsActionKind.ClearChatHistory -> viewModel.clearChatHistory(
                                authSession = sessionState.authSession,
                                onSuccess = {
                                    chatViewModel.clearConversation(companionName)
                                },
                                onUnauthorized = onUnauthorized,
                            )

                            SettingsActionKind.Logout -> viewModel.logout(
                                authSession = sessionState.authSession,
                                onSuccess = onLogout,
                            )

                            SettingsActionKind.DeleteAccount -> viewModel.deleteAccount(
                                authSession = sessionState.authSession,
                                onSuccess = onAccountDeleted,
                                onUnauthorized = onUnauthorized,
                            )
                        }
                    },
                    enabled = !uiState.isWorking,
                ) {
                    Text(dialog.confirmLabel)
                }
            },
            dismissButton = {
                Button(
                    onClick = { pendingAction = null },
                    enabled = !uiState.isWorking,
                ) {
                    Text("取消")
                }
            },
        )
    }
}

private enum class SettingsActionKind {
    ClearRecentCapture,
    ClearChatHistory,
    Logout,
    DeleteAccount,
}

private data class SettingsActionItem(
    val kind: SettingsActionKind,
    val label: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean,
    val destructive: Boolean = false,
)

private data class SettingsDialogCopy(
    val title: String,
    val message: String,
    val confirmLabel: String,
)

@Composable
private fun SettingsGroup(
    title: String,
    items: List<SettingsActionItem>,
    onItemClick: (SettingsActionItem) -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                SettingsRow(
                    item = item,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    item: SettingsActionItem,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, NightOutline.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (item.destructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SettingsActionKind.dialog(): SettingsDialogCopy {
    return when (this) {
        SettingsActionKind.ClearRecentCapture -> SettingsDialogCopy(
            title = "清除最近作品回流？",
            message = "这会移除聊天页里的最近作品卡片，但不会删除系统相册中的图片。",
            confirmLabel = "确认清除",
        )

        SettingsActionKind.ClearChatHistory -> SettingsDialogCopy(
            title = "清除同步聊天记录？",
            message = "服务端聊天历史会被重置，本地欢迎语和消息列表也会重新开始。",
            confirmLabel = "确认清除",
        )

        SettingsActionKind.Logout -> SettingsDialogCopy(
            title = "退出当前登录？",
            message = "退出后会返回邮箱验证码登录页，但不会删除你的账号资料。",
            confirmLabel = "退出登录",
        )

        SettingsActionKind.DeleteAccount -> SettingsDialogCopy(
            title = "删除账号数据？",
            message = "这个操作不可恢复，会清除账号级数据并返回登录页。",
            confirmLabel = "确认删除",
        )
    }
}
