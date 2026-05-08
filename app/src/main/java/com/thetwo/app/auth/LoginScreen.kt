package com.thetwo.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.network.AuthSession

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (AuthSession) -> Unit,
) {
    val uiState by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { viewModel.uiState } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "THETWO",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "当前先接开发验证码登录。流程会固定成两步：先发送验证码，再验证进入 THETWO。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::updateEmail,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.verificationCode,
                onValueChange = viewModel::updateVerificationCode,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("验证码") },
                supportingText = { Text("开发期会在发送验证码后展示调试验证码提示。") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = uiState.consentAccepted,
                    onCheckedChange = viewModel::updateConsentAccepted,
                )
                Text(
                    text = "我已阅读并同意隐私说明与数据处理说明。未勾选前不可请求验证码或登录。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            uiState.requestMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.debugCodeHint?.let { debugCode ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开发验证码：$debugCode",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::requestCode,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.consentAccepted && !uiState.isRequestingCode && !uiState.isVerifying,
                ) {
                    Text(if (uiState.isRequestingCode) "发送中..." else "发送验证码")
                }
                Button(
                    onClick = { viewModel.verifyCode(onLoginSuccess) },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.consentAccepted &&
                        uiState.isCodeRequested &&
                        !uiState.isVerifying &&
                        uiState.verificationCode.isNotBlank(),
                ) {
                    Text(if (uiState.isVerifying) "登录中..." else "进入 THETWO")
                }
            }
        }
    }
}
