@file:Suppress("RestrictedApi")

package com.rudimentor.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

enum class PaletteId(
    val code: String,
    val displayName: String,
    internal val seed: Long,
    internal val surface: Long,
    internal val surfaceContainerLow: Long,
    internal val surfaceContainer: Long,
) {
    P1("P1", "Brick Tomato", 0xFFE54D2E, 0xFF181111, 0xFF1F1513, 0xFF391714),
    P2("P2", "Oxide Bronze", 0xFFA18072, 0xFF141110, 0xFF1C1917, 0xFF262220),
    P3("P3", "Terracotta Brown", 0xFFAD7F58, 0xFF12110F, 0xFF1C1916, 0xFF28211D),
    P4("P4", "Oxblood Ruby", 0xFFE54666, 0xFF191113, 0xFF211216, 0xFF3A141E),
    P5("P5", "Deep Red + Sand", 0xFFE5484D, 0xFF191111, 0xFF211313, 0xFF35161A),
    P6("P6", "Carbon Brick", 0xFFC92A2A, 0xFF161616, 0xFF1C1C1C, 0xFF262626),
    ;

    companion object {
        val Default = P1

        fun fromSavedValue(value: String?): PaletteId = entries.firstOrNull { it.name == value } ?: Default
    }
}

private val materialColors = MaterialDynamicColors()

internal fun paletteColorScheme(palette: PaletteId): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(palette.seed.toInt()), true, 0.0)
    fun color(dynamicColor: com.google.android.material.color.utilities.DynamicColor): Color =
        Color(dynamicColor.getArgb(scheme))

    return darkColorScheme(
        primary = color(materialColors.primary()),
        onPrimary = color(materialColors.onPrimary()),
        primaryContainer = color(materialColors.primaryContainer()),
        onPrimaryContainer = color(materialColors.onPrimaryContainer()),
        inversePrimary = color(materialColors.inversePrimary()),
        secondary = color(materialColors.secondary()),
        onSecondary = color(materialColors.onSecondary()),
        secondaryContainer = color(materialColors.secondaryContainer()),
        onSecondaryContainer = color(materialColors.onSecondaryContainer()),
        tertiary = color(materialColors.tertiary()),
        onTertiary = color(materialColors.onTertiary()),
        tertiaryContainer = color(materialColors.tertiaryContainer()),
        onTertiaryContainer = color(materialColors.onTertiaryContainer()),
        background = Color(palette.surface),
        onBackground = color(materialColors.onBackground()),
        surface = Color(palette.surface),
        onSurface = color(materialColors.onSurface()),
        surfaceVariant = color(materialColors.surfaceVariant()),
        onSurfaceVariant = color(materialColors.onSurfaceVariant()),
        surfaceTint = color(materialColors.surfaceTint()),
        inverseSurface = color(materialColors.inverseSurface()),
        inverseOnSurface = color(materialColors.inverseOnSurface()),
        error = color(materialColors.error()),
        onError = color(materialColors.onError()),
        errorContainer = color(materialColors.errorContainer()),
        onErrorContainer = color(materialColors.onErrorContainer()),
        outline = color(materialColors.outline()),
        outlineVariant = color(materialColors.outlineVariant()),
        scrim = color(materialColors.scrim()),
        surfaceBright = color(materialColors.surfaceBright()),
        surfaceDim = color(materialColors.surfaceDim()),
        surfaceContainer = Color(palette.surfaceContainer),
        surfaceContainerHigh = color(materialColors.surfaceContainerHigh()),
        surfaceContainerHighest = color(materialColors.surfaceContainerHighest()),
        surfaceContainerLow = Color(palette.surfaceContainerLow),
        surfaceContainerLowest = color(materialColors.surfaceContainerLowest()),
    )
}

@Composable
fun RudiMentorTheme(
    paletteId: PaletteId = PaletteId.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = paletteColorScheme(paletteId),
        content = content,
    )
}
