package com.team.frameai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val FrameGold = Color(0xFFE4B15A)
internal val FrameInk = Color(0xFF090909)
internal val FramePaper = Color(0xFFF3EEE4)
internal val FrameReady = Color(0xFF8FD0A8)
internal val FrameMuted = Color(0xFFB7B1A6)
internal val FrameLine = Color(0x66FFFFFF)

private val Scheme = darkColorScheme(
    primary = FrameGold,
    onPrimary = Color(0xFF1C160C),
    background = FrameInk,
    onBackground = FramePaper,
    surface = Color(0xFF141414),
    onSurface = FramePaper,
    secondary = FrameMuted,
)

@Composable
fun FrameAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
