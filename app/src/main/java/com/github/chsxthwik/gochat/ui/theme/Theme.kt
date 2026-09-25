package com.github.chsxthwik.gochat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GoColors {
    val Bg = Color(0xFF0B0B0F)
    val Surface = Color(0xFF121217)
    val Surface2 = Color(0xFF1A1A21)
    val Glass = Color(0x99FFFFFF).copy(alpha = 0.04f)
    val GlassBorder = Color(0x1FFFFFFF)
    val Accent = Color(0xFFF59E0B)
    val AccentDim = Color(0xFF92610A)
    val Text = Color(0xFFE7E7EA)
    val TextDim = Color(0xFF9B9BA4)
    val TextFaint = Color(0xFF787884)
    val UserBubble = Color(0xFF1F1F28)
    val Error = Color(0xFFEF4444)
    val Ok = Color(0xFF34D399)
    val CodeBg = Color(0xFF0E0E13)
}

private val scheme = darkColorScheme(
    primary = GoColors.Accent,
    onPrimary = Color(0xFF14100A),
    secondary = GoColors.AccentDim,
    background = GoColors.Bg,
    onBackground = GoColors.Text,
    surface = GoColors.Surface,
    onSurface = GoColors.Text,
    surfaceVariant = GoColors.Surface2,
    onSurfaceVariant = GoColors.TextDim,
    error = GoColors.Error,
    outline = GoColors.GlassBorder,
)

private val typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.5.sp, lineHeight = 23.sp, color = GoColors.Text),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, color = GoColors.Text),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = GoColors.Text),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = GoColors.Text),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = GoColors.TextDim),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GoColors.TextDim),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, lineHeight = 20.sp, color = GoColors.Text)

@Composable
fun GoChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
