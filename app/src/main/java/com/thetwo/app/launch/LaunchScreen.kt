package com.thetwo.app.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppHero
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.GlassPanel

@Composable
fun LaunchScreen(
    viewModel: LaunchViewModel,
    onResolved: (LaunchResolution) -> Unit,
) {
    val uiState = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.bootstrap()
    }

    LaunchedEffect(uiState.resolution) {
        uiState.resolution?.let { resolution ->
            onResolved(resolution)
            viewModel.consumeResolution()
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AppHero(
                eyebrow = "THETWO",
                title = "回到那段还没说完的对话。",
                subtitle = "进入前，我们会先恢复你的登录状态、同伴设定和最近一次召唤截图。",
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在恢复会话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "正在检查你的登录状态、同伴资料和最近一次召唤截图。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (uiState.errorMessage != null) {
                    AppPill(
                        text = "会话恢复失败",
                        tone = AppChipTone.Danger,
                    )
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Button(
                        onClick = viewModel::bootstrap,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重试")
                    }
                } else {
                    CircularProgressIndicator()
                    Text(
                        text = "正在准备 THETWO",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
