package com.thetwo.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thetwo.app.session.AppSessionViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    sessionViewModel: AppSessionViewModel,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }
    val sessionState by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { sessionViewModel.uiState } }
    val companionName = sessionState.companionProfile?.nickname ?: "角色"

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "THETWO 聊天首页 · $companionName",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSummon) {
                        Text("召唤")
                    }
                    Button(onClick = onOpenSettings) {
                        Text("设置")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "阶段 1 目标：先把聊天主闭环做成可演示版本，召唤作为增强入口保留。",
                style = MaterialTheme.typography.bodyMedium,
            )
            sessionState.loginEmail?.let { email ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "当前账号：$email",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (uiState.isRestrictedMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前处于安全/受限模式，回复会更保守，且不展示主动召唤邀约。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            sessionState.recentCaptureReference?.let { capture ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(capture.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(capture.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(capture.storageLocation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    val bubbleColor = if (message.author == MessageAuthor.USER) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bubbleColor, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = if (message.author == MessageAuthor.USER) "你" else "角色",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.draft,
                onValueChange = viewModel::updateDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("输入消息") },
                supportingText = {
                    Text("可测试关键字：未成年、自杀、不想活，用于验证安全/受限模式")
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = viewModel::sendMessage,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isReplying,
            ) {
                Text(if (uiState.isReplying) "角色回复中..." else "发送")
            }
        }
    }
}
