package com.rudimentor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RudiMentorColors = darkColorScheme(
    primary = Color(0xFFD8A09A),
    onPrimary = Color(0xFF3B0908),
    primaryContainer = Color(0xFF7A3E3A),
    onPrimaryContainer = Color(0xFFFFEDEA),
    secondary = Color(0xFFC9B8B4),
    onSecondary = Color(0xFF312A28),
    secondaryContainer = Color(0xFF47403E),
    onSecondaryContainer = Color(0xFFF0E2DE),
    background = Color(0xFF11100F),
    onBackground = Color(0xFFE9E1DF),
    surface = Color(0xFF181716),
    onSurface = Color(0xFFE9E1DF),
    surfaceVariant = Color(0xFF292624),
    onSurfaceVariant = Color(0xFFCEC3C0),
    outline = Color(0xFF8E817E),
    error = Color(0xFFE8A19A),
)

@Composable
fun RudiMentorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RudiMentorColors,
        content = content,
    )
}
