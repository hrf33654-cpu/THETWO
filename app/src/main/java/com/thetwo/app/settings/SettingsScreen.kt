package com.thetwo.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.session.AppSessionViewModel

@Composable
fun SettingsScreen(
    sessionViewModel: AppSessionViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val sessionState by remember { androidx.compose.runtime.derivedStateOf { sessionViewModel.uiState } }
    var feedback by remember { mutableStateOf<String?>(null) }
    val companionName = sessionState.companionProfile?.nickname ?: "角色"

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "设置与数据",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "这里补齐 MVP 的隐私说明、权限边界和清理入口。当前不会删除系统相册中的图片文件。",
                style = MaterialTheme.typography.bodyMedium,
            )

            SettingsCard(
                title = "隐私与相机说明",
                body = "召唤页使用相机画面生成截图，不做房间建模。保存截图后，App 只保留最近一次作品回流元信息，用于回到聊天时引用。",
            )

            SettingsCard(
                title = "权限边界",
                body = "当前只申请相机权限。Android 10 及以上通过 MediaStore 写入系统相册；旧版 Android 保存到应用图片目录，不申请读取媒体权限。",
            )

            sessionState.recentCaptureReference?.let { capture ->
                SettingsCard(
                    title = "最近作品回流",
                    body = "${capture.title}\n${capture.summary}\n${capture.storageLocation}",
                )
                OutlinedButton(
                    onClick = {
                        sessionViewModel.clearRecentCapture()
                        chatViewModel.clearRecentCaptureReference()
                        feedback = "已清除 App 内最近作品回流记录，不会删除系统相册文件。"
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清除最近作品回流")
                }
            }

            OutlinedButton(
                onClick = {
                    chatViewModel.clearConversation(companionName)
                    feedback = "已清空当前聊天记录，角色会从新的欢迎消息重新开始。"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空聊天记录")
            }

            OutlinedButton(
                onClick = {
                    feedback = "账号数据删除入口已预留。MVP 下一步需要接真实后端删除接口和删除状态反馈。"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("账号数据删除入口")
            }

            OutlinedButton(
                onClick = {
                    feedback = "未成年人和危机内容会切到更保守的回复模式，且不展示主动召唤邀约文案。"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看安全策略说明")
            }

            feedback?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("返回聊天")
            }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
