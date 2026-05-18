package com.thetwo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thetwo.app.ui.theme.Aurora
import com.thetwo.app.ui.theme.Frost
import com.thetwo.app.ui.theme.Ink
import com.thetwo.app.ui.theme.Midnight
import com.thetwo.app.ui.theme.Mist
import com.thetwo.app.ui.theme.Moonlight
import com.thetwo.app.ui.theme.NightOutline
import com.thetwo.app.ui.theme.NightSurface
import com.thetwo.app.ui.theme.NightSurfaceElevated
import com.thetwo.app.ui.theme.RoseMist
import com.thetwo.app.ui.theme.SoftRose

enum class AppChipTone {
    Default,
    Accent,
    Success,
    Warning,
    Danger,
}

private val PanelShape = RoundedCornerShape(28.dp)

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFBFD),
                        Color(0xFFFFF4FA),
                        Color(0xFFFDF2F8),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-72).dp, y = (-24).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            RoseMist.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        radius = 300f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 92.dp, y = (-28).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Aurora.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                        radius = 320f,
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-120).dp, y = 72.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SoftRose.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                        radius = 320f,
                    ),
                    CircleShape,
                ),
        )
        content()
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    tonal: Color = NightSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(PanelShape)
            .background(tonal)
            .border(1.dp, NightOutline.copy(alpha = 0.72f), PanelShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun AppPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: AppChipTone = AppChipTone.Default,
) {
    val (background, foreground, stroke) = when (tone) {
        AppChipTone.Default -> Triple(Color(0xFFF6ECF3), Ink, NightOutline.copy(alpha = 0.35f))
        AppChipTone.Accent -> Triple(
            Brush.horizontalGradient(listOf(Aurora, RoseMist)).toSolidFallback(Aurora),
            Frost,
            Color.Transparent,
        )

        AppChipTone.Success -> Triple(Color(0xFFE9FBEE), Color(0xFF207445), Color(0xFFBEE7CC))
        AppChipTone.Warning -> Triple(Color(0xFFFFF4DC), Color(0xFF946200), Color(0xFFF2D99B))
        AppChipTone.Danger -> Triple(Color(0xFFFFE8EE), Color(0xFFAC3F62), Color(0xFFF5C5D2))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(1.dp, stroke, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
        )
    }
}

@Composable
fun AppHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppPill(text = eyebrow, tone = AppChipTone.Accent)
            Spacer(modifier = Modifier.weight(1f))
            if (trailing != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailing,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Mist,
        )
    }
}

@Composable
fun CompanionBadge(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "T"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        RoseMist.copy(alpha = 0.95f),
                        Aurora.copy(alpha = 0.85f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = Midnight,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun MetadataLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Mist,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = Mist,
    )
}

@Composable
fun ElevatedSeparator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NightOutline.copy(alpha = 0.55f)),
    )
}

@Composable
fun HighlightPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        tonal = NightSurfaceElevated,
        content = content,
    )
}

private fun Brush.toSolidFallback(fallback: Color): Color = fallback
