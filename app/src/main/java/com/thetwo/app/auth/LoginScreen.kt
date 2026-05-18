package com.thetwo.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.GlassPanel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onCodeRequested: () -> Unit,
) {
    val uiState by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "THETWO",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "进入你的陪伴空间",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "先输入邮箱，我们会把 6 位验证码发给你。登录后就能继续你的聊天、召唤和最近作品回流。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "登录",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::updateEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("邮箱地址") },
                    placeholder = { Text("name@example.com") },
                    singleLine = true,
                )
                ConsentRow(
                    checked = uiState.consentAccepted,
                    text = "我已阅读并同意隐私说明与数据处理规则",
                    onCheckedChange = viewModel::updateConsentAccepted,
                )
                ConsentRow(
                    checked = uiState.ageConfirmed,
                    text = "我确认自己已年满 18 岁",
                    onCheckedChange = viewModel::updateAgeConfirmed,
                )

                uiState.requestMessage?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Success)
                }
                uiState.errorMessage?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Danger)
                }

                Button(
                    onClick = { viewModel.requestCode(onCodeRequested) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRequestingCode,
                ) {
                    Text(if (uiState.isRequestingCode) "发送中..." else "获取验证码")
                }
            }

            Text(
                text = "首次进入前，你将在下一页完成验证码验证。系统只会使用必要的登录、聊天同步和截图保存权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    text: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = text,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
