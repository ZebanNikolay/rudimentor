package com.rudimentor.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PaletteContrastTest {
    @Test
    fun `all palettes meet text component and dark-surface contrast guardrails`() {
        PaletteId.entries.forEach { palette ->
            val colors = paletteColorScheme(palette)

            assertContrast(palette, "onSurface/surface", colors.onSurface, colors.surface, 4.5)
            assertContrast(palette, "onSurfaceVariant/surface", colors.onSurfaceVariant, colors.surface, 4.5)
            assertContrast(palette, "primary/surface", colors.primary, colors.surface, 3.0)
            assertContrast(
                palette,
                "onPrimaryContainer/primaryContainer",
                colors.onPrimaryContainer,
                colors.primaryContainer,
                4.5,
            )
            assertContrast(palette, "white/surface", Color.White, colors.surface, 15.8)
        }
    }

    private fun assertContrast(
        palette: PaletteId,
        pair: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("${palette.code} $pair contrast was $ratio, expected at least $minimum", ratio >= minimum)
    }
}

private fun contrastRatio(first: Color, second: Color): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    return (max(firstLuminance, secondLuminance) + 0.05) /
        (min(firstLuminance, secondLuminance) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    val argb = color.toArgb()
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(argb shr 16 and 0xFF) +
        0.7152 * linear(argb shr 8 and 0xFF) +
        0.0722 * linear(argb and 0xFF)
}
