package com.rootfix.app.ui.extra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Reusable low-contrast glass panel with soft neumorphic depth for app screens. */
@Composable
fun RootFixGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val panelBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.075f),
            tint.copy(alpha = 0.94f),
            tint.copy(alpha = 0.82f)
        )
    )
    val edgeBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.045f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    )

    Column(
        modifier = modifier
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.38f), spotColor = Color.Black.copy(alpha = 0.48f))
            .clip(shape)
            .background(panelBrush)
            .border(1.dp, edgeBrush, shape),
        content = content
    )
}
