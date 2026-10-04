package com.rootfix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RootFixDarkColors = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color(0xFF092117),
    secondary = AccentCyan,
    onSecondary = Color(0xFF082023),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = Color(0xFF280909)
)

@Composable
fun RootFixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RootFixDarkColors,
        content = content
    )
}
