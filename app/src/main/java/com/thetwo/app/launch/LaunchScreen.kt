package com.thetwo.app.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "THETWO",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isRestoring) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在恢复会话与最近状态…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = viewModel::bootstrap,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试恢复")
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
