package com.thetwo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Aurora,
    secondary = RoseMist,
    tertiary = Color(0xFF59C66E),
    background = Moonlight,
    surface = NightSurface,
    surfaceVariant = NightSurfaceSoft,
    primaryContainer = Color(0xFFFFE1F5),
    secondaryContainer = Color(0xFFF1E8FF),
    onPrimary = Color.White,
    onSecondary = Midnight,
    onSurface = Ink,
    onSurfaceVariant = Mist,
    outline = NightOutline,
)

@Composable
fun THETWOTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content,
    )
}
