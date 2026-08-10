package com.rudimentor.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rudimentor.app.R

/** Space Grotesk — interface type and the logo word. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_medium, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** JetBrains Mono — numerals and hand letters. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Tabular figures keep BPM and the timer from jittering between digits. */
private const val TABULAR = "tnum"

val RudiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 52.sp,
        fontFeatureSettings = TABULAR,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5f.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5f.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.14f.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = RudiDimens.LabelTracking,
    ),
)

/** Styles that are specific to the drum-machine chrome. */
object RudiTextStyles {
    /** Uppercase rubric, Space Grotesk 600, tracking 0.18em. */
    val Rubric = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = RudiDimens.LabelTracking,
    )

    /** Large BPM readout. */
    val BpmValue = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 52.sp,
        fontFeatureSettings = TABULAR,
    )

    /** Session timer in the top bar. */
    val Timer = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        fontFeatureSettings = TABULAR,
    )

    /** Stepper value. */
    val StepperValue = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        fontFeatureSettings = TABULAR,
    )

    /** Row number next to the beats. */
    val RowNumber = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        fontFeatureSettings = TABULAR,
    )

    val RowNumberActive = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        fontFeatureSettings = TABULAR,
    )
}
