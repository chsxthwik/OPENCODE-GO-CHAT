package com.github.chsxthwik.gochat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val scheme = darkColorScheme(
    primary = GoColors.Accent,
    onPrimary = GoColors.OnAccent,
    primaryContainer = GoColors.AccentSoft,
    onPrimaryContainer = GoColors.Accent,
    secondary = GoColors.TextDim,
    onSecondary = GoColors.Text,
    background = GoColors.Bg,
    onBackground = GoColors.Text,
    surface = GoColors.Surface,
    onSurface = GoColors.Text,
    surfaceVariant = GoColors.SurfaceHigh,
    onSurfaceVariant = GoColors.TextDim,
    surfaceContainerHighest = GoColors.SurfaceTop,
    error = GoColors.Error,
    onError = GoColors.Text,
    errorContainer = GoColors.ErrorSoft,
    onErrorContainer = GoColors.Error,
    outline = GoColors.LineStrong,
    outlineVariant = GoColors.Line,
    scrim = GoColors.Bg.copy(alpha = 0.7f),
)

private val typography = Typography(
    displaySmall = GoType.Display,
    headlineSmall = GoType.Headline,
    titleLarge = GoType.Headline,
    titleMedium = GoType.Title,
    titleSmall = GoType.TitleSmall,
    bodyLarge = GoType.Body,
    bodyMedium = GoType.BodyDim,
    bodySmall = GoType.BodySmall,
    labelLarge = GoType.Label,
    labelMedium = GoType.Label,
    labelSmall = GoType.Caption,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(GoShape.Xs),
    small = RoundedCornerShape(GoShape.S),
    medium = RoundedCornerShape(GoShape.M),
    large = RoundedCornerShape(GoShape.L),
    extraLarge = RoundedCornerShape(GoShape.Xl),
)

@Composable
fun GoChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
