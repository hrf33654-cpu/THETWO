package com.thetwo.app.companion

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CompanionSetupScreen(
    viewModel: CompanionSetupViewModel,
    onProfileReady: (CompanionProfile) -> Unit,
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
                text = "创建你的陪伴角色",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "阶段 1 先落基础人设输入，后续再接更完整的角色设定流程。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = uiState.nickname,
                onValueChange = viewModel::updateNickname,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("昵称") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.tone,
                onValueChange = viewModel::updateTone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("语气") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.personalityTagsInput,
                onValueChange = viewModel::updatePersonalityTagsInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("人格标签") },
                supportingText = { Text("用英文逗号分隔") },
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.interestTagsInput,
                onValueChange = viewModel::updateInterestTagsInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("兴趣标签") },
                supportingText = { Text("用英文逗号分隔") },
            )
            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val profile = viewModel.buildProfile()
                    if (profile != null) {
                        onProfileReady(profile)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("进入聊天首页")
            }
        }
    }
}
