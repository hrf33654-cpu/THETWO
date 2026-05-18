package com.thetwo.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.chat.ChatScreen
import com.thetwo.app.chat.ChatViewModel
import com.thetwo.app.session.AppSessionViewModel
import com.thetwo.app.settings.SettingsScreen
import com.thetwo.app.settings.SettingsViewModel
import com.thetwo.app.summon.SummonExperienceScreen
import com.thetwo.app.summon.SummonViewModel
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.theme.Aurora
import com.thetwo.app.ui.theme.DividerSoft
import com.thetwo.app.ui.theme.NightSurface

private enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    Chat("聊天", Icons.AutoMirrored.Rounded.Chat),
    Summon("召唤", Icons.Rounded.AutoAwesome),
    Settings("设置", Icons.Rounded.Settings),
}

@Composable
fun MainShell(
    chatViewModel: ChatViewModel,
    sessionViewModel: AppSessionViewModel,
    summonViewModel: SummonViewModel,
    settingsViewModel: SettingsViewModel,
    onProfileRequired: () -> Unit,
    onUnauthorized: (String) -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Chat) }

    AppBackground {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = Color.White,
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    color = NightSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(30.dp),
                    border = BorderStroke(1.dp, DividerSoft),
                    shadowElevation = 18.dp,
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MainTab.entries.forEach { tab ->
                            MainTabButton(
                                modifier = Modifier.weight(1f),
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                label = tab.label,
                                icon = tab.icon,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            MainShellContent(
                currentTab = currentTab,
                contentPadding = innerPadding,
                chatViewModel = chatViewModel,
                sessionViewModel = sessionViewModel,
                summonViewModel = summonViewModel,
                settingsViewModel = settingsViewModel,
                onOpenSummon = { currentTab = MainTab.Summon },
                onOpenSettings = { currentTab = MainTab.Settings },
                onBackToChat = { currentTab = MainTab.Chat },
                onProfileRequired = onProfileRequired,
                onUnauthorized = onUnauthorized,
                onLogout = onLogout,
                onAccountDeleted = onAccountDeleted,
            )
        }
    }
}

@Composable
private fun MainShellContent(
    currentTab: MainTab,
    contentPadding: PaddingValues,
    chatViewModel: ChatViewModel,
    sessionViewModel: AppSessionViewModel,
    summonViewModel: SummonViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenSummon: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToChat: () -> Unit,
    onProfileRequired: () -> Unit,
    onUnauthorized: (String) -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentTab) {
            MainTab.Chat -> ChatScreen(
                viewModel = chatViewModel,
                sessionViewModel = sessionViewModel,
                onOpenSummon = onOpenSummon,
                onOpenSettings = onOpenSettings,
                onProfileRequired = onProfileRequired,
                onUnauthorized = { onUnauthorized("chat") },
                contentPadding = contentPadding,
            )

            MainTab.Summon -> SummonExperienceScreen(
                viewModel = summonViewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = onBackToChat,
                onOpenCapturePreview = {},
                onUnauthorized = { onUnauthorized("summon") },
                contentPadding = contentPadding,
            )

            MainTab.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                sessionViewModel = sessionViewModel,
                chatViewModel = chatViewModel,
                onBack = onBackToChat,
                onUnauthorized = { onUnauthorized("settings") },
                onLogout = onLogout,
                onAccountDeleted = onAccountDeleted,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun MainTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Color.White.copy(alpha = 0.09f) else Color.Transparent,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            1.dp,
            if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = if (selected) Aurora.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.68f),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
