package com.rothrockware.studyjazzstandards.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette lifted from the web app's CSS custom properties. */
object JazzColors {
    val Bg = Color(0xFF0E0E0E)
    val Bg2 = Color(0xFF161616)
    val Bg3 = Color(0xFF1E1E1E)
    val Border = Color(0xFF2A2A2A)
    val Border2 = Color(0xFF333333)
    val Text = Color(0xFFE8E4DC)
    val Text2 = Color(0xFF888888)
    val Text3 = Color(0xFF555555)
    val Gold = Color(0xFFC9A84C)
    val Gold2 = Color(0xFFE8C97A)
    val GoldDim = Color(0x1FC9A84C)
    val Red = Color(0xFFC0392B)
    val RedDim = Color(0x1FC0392B)
    val Green = Color(0xFF27AE60)
    val GreenDim = Color(0x1F27AE60)
    val Blue = Color(0xFF2980B9)
    val BlueDim = Color(0x1F2980B9)
}

private val JazzDarkColorScheme = darkColorScheme(
    primary = JazzColors.Gold,
    onPrimary = JazzColors.Bg,
    primaryContainer = JazzColors.GoldDim,
    onPrimaryContainer = JazzColors.Gold2,
    secondary = JazzColors.Blue,
    onSecondary = JazzColors.Text,
    error = JazzColors.Red,
    onError = JazzColors.Text,
    background = JazzColors.Bg,
    onBackground = JazzColors.Text,
    surface = JazzColors.Bg2,
    onSurface = JazzColors.Text,
    surfaceVariant = JazzColors.Bg3,
    onSurfaceVariant = JazzColors.Text2,
    outline = JazzColors.Border2,
    outlineVariant = JazzColors.Border,
    surfaceContainer = JazzColors.Bg2,
    surfaceContainerHigh = JazzColors.Bg3,
    surfaceContainerHighest = JazzColors.Bg3,
)

@Composable
fun JazzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JazzDarkColorScheme,
        content = content,
    )
}
