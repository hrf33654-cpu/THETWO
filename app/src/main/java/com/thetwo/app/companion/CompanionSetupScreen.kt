package com.thetwo.app.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetwo.app.network.AuthSession
import com.thetwo.app.ui.AppBackground
import com.thetwo.app.ui.AppChipTone
import com.thetwo.app.ui.AppHero
import com.thetwo.app.ui.AppPill
import com.thetwo.app.ui.CompanionBadge
import com.thetwo.app.ui.GlassPanel
import com.thetwo.app.ui.HighlightPanel
import com.thetwo.app.ui.MetadataLine
import com.thetwo.app.ui.theme.Aurora
import com.thetwo.app.ui.theme.Ink
import com.thetwo.app.ui.theme.NightOutline
import com.thetwo.app.ui.theme.RoseMist

@Composable
fun CompanionSetupScreen(
    viewModel: CompanionSetupViewModel,
    authSession: AuthSession?,
    onProfileReady: (CompanionProfile) -> Unit,
    onUnauthorized: () -> Unit,
) {
    val uiState = viewModel.uiState
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    val toneOptions = listOf("温柔陪伴", "俏皮活泼", "冷静克制", "姐姐感")
    val personalityOptions = listOf("温柔", "敏锐", "克制", "黏人", "毒舌", "坚定")
    val interestOptions = listOf("夜空", "音乐", "摄影", "电影", "游戏", "旅行")
    val previewName = uiState.nickname.ifBlank { "灵儿" }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHero(
                eyebrow = "创建角色",
                title = "把你的陪伴感先定下来",
                subtitle = "按照 Figma 里的 4 步流程，我们先给 TA 一个名字、语气和兴趣，再进入聊天首页。",
            )

            StepProgress(
                currentStep = currentStep,
                labels = listOf("命名", "语气", "标签", "确认"),
            )

            HighlightPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CompanionBadge(name = previewName, size = 64.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = previewName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.tone.ifBlank { "还没有设定语气" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MetadataLine(
                    label = "当前账号",
                    value = authSession?.email ?: "登录会话失效后需要重新验证",
                )
            }

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                when (currentStep) {
                    0 -> StepNickname(
                        nickname = uiState.nickname,
                        onNicknameChange = viewModel::updateNickname,
                    )

                    1 -> StepTone(
                        tone = uiState.tone,
                        toneOptions = toneOptions,
                        onToneSelected = viewModel::updateTone,
                        onToneChanged = viewModel::updateTone,
                    )

                    2 -> StepTags(
                        personalityInput = uiState.personalityTagsInput,
                        interestInput = uiState.interestTagsInput,
                        personalityOptions = personalityOptions,
                        interestOptions = interestOptions,
                        onPersonalityChanged = viewModel::updatePersonalityTagsInput,
                        onInterestChanged = viewModel::updateInterestTagsInput,
                    )

                    else -> StepConfirm(
                        nickname = previewName,
                        tone = uiState.tone,
                        personalityTags = uiState.personalityTagsInput,
                        interestTags = uiState.interestTagsInput,
                    )
                }

                uiState.errorMessage?.let { message ->
                    AppPill(text = message, tone = AppChipTone.Danger)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentStep > 0) {
                                currentStep -= 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = currentStep > 0 && !uiState.isSaving,
                    ) {
                        Text("上一步")
                    }
                    if (currentStep < 3) {
                        Button(
                            onClick = { currentStep += 1 },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("下一步")
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.submitProfile(
                                    authSession = authSession,
                                    onSuccess = onProfileReady,
                                    onUnauthorized = onUnauthorized,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isSaving,
                        ) {
                            Text(if (uiState.isSaving) "创建中…" else "进入 THETWO")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepProgress(
    currentStep: Int,
    labels: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isActive = index == currentStep
            val isComplete = index < currentStep
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                color = if (isActive || isComplete) Color.Transparent else MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = if (isActive || isComplete) {
                                Brush.horizontalGradient(listOf(Aurora, RoseMist))
                            } else {
                                Brush.horizontalGradient(listOf(Color.White, Color.White))
                            },
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "${index + 1}. $label",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive || isComplete) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepNickname(
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "1 / 4 角色命名",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "先给陪伴角色起一个你愿意在聊天里一直叫的名字。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("角色名字") },
            placeholder = { Text("例如：灵儿、Aurine、小夜") },
        )
    }
}

@Composable
private fun StepTone(
    tone: String,
    toneOptions: List<String>,
    onToneSelected: (String) -> Unit,
    onToneChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "2 / 4 语气选择",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "这会直接影响首轮欢迎语和后续聊天气质。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceChips(
            options = toneOptions,
            selected = setOf(tone),
            onToggle = onToneSelected,
        )
        OutlinedTextField(
            value = tone,
            onValueChange = onToneChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("自定义语气") },
            placeholder = { Text("例如：温柔，稳定，带一点疏离感") },
        )
    }
}

@Composable
private fun StepTags(
    personalityInput: String,
    interestInput: String,
    personalityOptions: List<String>,
    interestOptions: List<String>,
    onPersonalityChanged: (String) -> Unit,
    onInterestChanged: (String) -> Unit,
) {
    val personalitySelected = parseTags(personalityInput)
    val interestSelected = parseTags(interestInput)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "3 / 4 个性与兴趣",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "先用标签把角色轮廓勾出来，后面聊天里还能继续细化。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "性格标签",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        ChoiceChips(
            options = personalityOptions,
            selected = personalitySelected,
            onToggle = { option ->
                onPersonalityChanged(toggleTag(personalitySelected, option))
            },
        )
        OutlinedTextField(
            value = personalityInput,
            onValueChange = onPersonalityChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("更多性格标签") },
            placeholder = { Text("多个标签用逗号分隔") },
        )

        Text(
            text = "兴趣标签",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        ChoiceChips(
            options = interestOptions,
            selected = interestSelected,
            onToggle = { option ->
                onInterestChanged(toggleTag(interestSelected, option))
            },
        )
        OutlinedTextField(
            value = interestInput,
            onValueChange = onInterestChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("更多兴趣标签") },
            placeholder = { Text("多个标签用逗号分隔") },
        )
    }
}

@Composable
private fun StepConfirm(
    nickname: String,
    tone: String,
    personalityTags: String,
    interestTags: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "4 / 4 最后确认",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "这就是聊天首页里会出现的角色气质，确认后就进入主界面。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetadataLine(label = "名字", value = nickname)
                MetadataLine(label = "语气", value = tone.ifBlank { "未设置" })
                MetadataLine(
                    label = "性格标签",
                    value = parseTags(personalityTags).joinToString(" / ").ifBlank { "未设置" },
                )
                MetadataLine(
                    label = "兴趣标签",
                    value = parseTags(interestTags).joinToString(" / ").ifBlank { "未设置" },
                )
            }
        }
    }
}

@Composable
private fun ChoiceChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowOptions.forEach { option ->
                    val isSelected = option in selected
                    Surface(
                        onClick = { onToggle(option) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    brush = if (isSelected) {
                                        Brush.horizontalGradient(listOf(Aurora, RoseMist))
                                    } else {
                                        Brush.horizontalGradient(listOf(Color.White, Color.White))
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                repeat(3 - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun parseTags(raw: String): Set<String> {
    return raw.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
}

private fun toggleTag(
    current: Set<String>,
    option: String,
): String {
    val updated = current.toMutableSet().apply {
        if (!add(option)) {
            remove(option)
        }
    }
    return updated.joinToString(", ")
}
