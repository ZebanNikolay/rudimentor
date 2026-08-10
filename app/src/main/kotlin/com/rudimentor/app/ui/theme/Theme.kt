package com.rudimentor.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The one and only RudiMentor colour scheme: carbon body, brick accent.
 * Dynamic color and light theme are intentionally not supported (brandbook §6).
 */
fun brandColorScheme(): ColorScheme = darkColorScheme(
    primary = RudiColors.Brick,
    onPrimary = RudiColors.Text,
    primaryContainer = RudiColors.Brick,
    onPrimaryContainer = RudiColors.Text,
    secondary = RudiColors.Muted,
    onSecondary = RudiColors.Bg,
    secondaryContainer = RudiColors.Surface,
    onSecondaryContainer = RudiColors.Text,
    tertiary = RudiColors.BrickLit,
    onTertiary = RudiColors.Text,
    background = RudiColors.Bg,
    onBackground = RudiColors.Text,
    surface = RudiColors.Bg,
    onSurface = RudiColors.Text,
    surfaceVariant = RudiColors.Surface,
    onSurfaceVariant = RudiColors.Muted,
    surfaceContainerLowest = RudiColors.Bg,
    surfaceContainerLow = RudiColors.SurfaceAlt,
    surfaceContainer = RudiColors.Surface,
    surfaceContainerHigh = RudiColors.Surface,
    surfaceContainerHighest = RudiColors.Surface,
    surfaceDim = RudiColors.Bg,
    surfaceBright = RudiColors.Surface,
    outline = RudiColors.Line,
    outlineVariant = RudiColors.Line,
    scrim = RudiColors.Scrim,
    error = RudiColors.BrickLit,
    onError = RudiColors.Text,
)

@Composable
fun RudiMentorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = brandColorScheme(),
        typography = RudiTypography,
        content = content,
    )
}
