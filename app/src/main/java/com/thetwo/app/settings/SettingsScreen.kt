package com.thetwo.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.session.AppSessionViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
    onUnauthorized: () -> Unit = {},
    onAccountDeleted: () -> Unit = {},
) {
    val sessionState = sessionViewModel.uiState
    val uiState = viewModel.uiState
    val companionName = sessionState.companionProfile?.nickname ?: "角色"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(
                title = "设置",
                body = "这里放当前版本最重要的清理与说明入口。",
            )

            SettingsCard(
                title = "隐私与相机",
                body = "召唤页会使用相机画面生成截图，不做房间建模。保存后，App 只保留最近一次召唤记录，用于回到聊天时继续衔接。",
            )

            SettingsCard(
                title = "存储边界",
                body = "当前版本不会替你删除系统相册中的图片。你在这里清除的，只是 THETWO 内部的最近召唤记录。",
            )

            sessionState.recentCaptureReference?.let { capture ->
                SettingsCard(
                    title = capture.title,
                    body = "${capture.summary}\n${capture.storageLocation}",
                )
                OutlinedButton(
                    onClick = {
                        viewModel.clearRecentCapture(
                            authSession = sessionState.authSession,
                            onSuccess = {
                                sessionViewModel.clearRecentCapture()
                                chatViewModel.clearRecentCaptureReference()
                            },
                            onUnauthorized = {
                                sessionViewModel.clearAuthenticatedState()
                                chatViewModel.clearRecentCaptureReference()
                                chatViewModel.clearConversation(companionName)
                                onUnauthorized()
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isWorking,
                ) {
                    Text("清除最近作品回流")
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.clearChatHistory(
                        authSession = sessionState.authSession,
                        onSuccess = {
                            chatViewModel.clearConversation(companionName)
                        },
                        onUnauthorized = {
                            sessionViewModel.clearAuthenticatedState()
                            chatViewModel.clearConversation(companionName)
                            onUnauthorized()
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isWorking,
            ) {
                Text("清空当前聊天")
            }

            OutlinedButton(
                onClick = {
                    viewModel.deleteAccount(
                        authSession = sessionState.authSession,
                        onSuccess = {
                            sessionViewModel.clearAuthenticatedState()
                            chatViewModel.clearRecentCaptureReference()
                            chatViewModel.clearConversation("角色")
                            onAccountDeleted()
                        },
                        onUnauthorized = {
                            sessionViewModel.clearAuthenticatedState()
                            chatViewModel.clearConversation(companionName)
                            onUnauthorized()
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isWorking,
            ) {
                Text("账号数据删除入口")
            }

            uiState.feedbackMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回聊天")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
