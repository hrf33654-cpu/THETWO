package com.thetwo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Aurora,
    secondary = RoseMist,
    tertiary = Moonlight,
    background = Midnight,
    surface = NightSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = Twilight,
    secondary = RoseMist,
    tertiary = Aurora,
    background = Moonlight,
    surface = Moonlight,
)

@Composable
fun THETWOTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
