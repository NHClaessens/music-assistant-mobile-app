package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Protection shade over just the status-bar inset strip so the system clock/icons stay legible
 * against arbitrary content drawn beneath them under edge-to-edge, while content stays visible
 * through it. A straight top-to-bottom gradient fades to transparent by the strip's bottom, so
 * nothing below the status bar is dimmed and there's no hard bottom edge.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    @Suppress("MagicNumber") // Visual design tokens, after Android's DefaultLight/DarkScrim.
    val topAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.5f else 0.9f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = topAlpha),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
