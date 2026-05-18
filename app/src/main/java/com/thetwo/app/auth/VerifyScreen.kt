package com.thetwo.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thetwo.app.network.AuthSession
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.GlassPanel

@Composable
fun VerifyScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onLoginSuccess: (AuthSession) -> Unit,
) {
    val uiState by remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }

    AppBackground {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text("返回")
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "验证你的邮箱",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "验证码已发送到 ${uiState.email.ifBlank { "你的邮箱" }}。输入 6 位数字后，我们就带你回到灵儿身边。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "验证码",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                OtpCodeField(
                    code = uiState.verificationCode,
                    onCodeChange = viewModel::updateVerificationCode,
                )

                uiState.requestMessage?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Success)
                }
                uiState.debugCodeHint?.let { debugCode ->
                    AppPill(text = "调试验证码：$debugCode", tone = AppChipTone.Warning)
                }
                uiState.errorMessage?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Danger)
                }

                Text(
                    text = if (uiState.resendCooldownSeconds > 0) {
                        "${uiState.resendCooldownSeconds}s 后可重新发送验证码"
                    } else {
                        "没有收到验证码？可以重新发送"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = { viewModel.requestCode() },
                        enabled = uiState.resendCooldownSeconds == 0 && !uiState.isRequestingCode,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (uiState.isRequestingCode) "发送中..." else "重新发送")
                    }
                    Button(
                        onClick = { viewModel.verifyCode(onLoginSuccess) },
                        enabled = uiState.verificationCode.length == 6 && !uiState.isVerifying,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (uiState.isVerifying) "验证中..." else "进入 THETWO")
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
) {
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(6) { index ->
                    val char = code.getOrNull(index)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(52.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .border(
                                width = 1.dp,
                                color = if (index == code.length.coerceAtMost(5)) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable { },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    )
}
