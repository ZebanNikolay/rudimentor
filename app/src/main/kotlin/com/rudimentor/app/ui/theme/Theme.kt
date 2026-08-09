package com.rudimentor.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RudiMentorColors = darkColorScheme(
    primary = Color(0xFFFF3B3B),
    onPrimary = Color(0xFF240000),
    primaryContainer = Color(0xFF5B1010),
    onPrimaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF090909),
    onBackground = Color(0xFFF5F0F0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFF5F0F0),
    surfaceVariant = Color(0xFF242020),
    onSurfaceVariant = Color(0xFFD8C2C0),
    outline = Color(0xFF655B5A),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RudiMentorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RudiMentorColors,
        content = content,
    )
}
