package com.rudimentor.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for the RudiMentor brand palette.
 *
 * Values come from `docs/brandbook.md` (approved 2026-08-10). The app has one dark
 * theme only: no light theme, no dynamic color, no palette switcher.
 */
object RudiColors {
    val Bg = Color(0xFF161616)
    val Surface = Color(0xFF262626)
    val SurfaceAlt = Color(0xFF1C1C1C)
    val Line = Color(0xFF343434)
    val Text = Color(0xFFEEEEEC)
    val Muted = Color(0xFF8D8D8A)
    val Brick = Color(0xFFC92A2A)
    val BrickLit = Color(0xFFE03131)

    /** Pad internals. */
    val PadLed = Color(0xFF3C3C3C)
    val PadLedRound = Color(0xFF5F5F5F)
    val PadLedLit = Color(0xFFFFD7D7)
    val PadLedMute = Color(0xFF242424)
    val PadLetter = Muted
    val PadLetterAccent = Color(0xFFD9A8A8)
    val PadLetterMute = Color(0xFF4A4A48)
    val PadLetterLit = Color(0xFFFFFFFF)
    val PadMuteBorder = Color(0xFF2F2F2F)
    val PadAccentLit = Color(0xFFE8332F)
    val PadMuteLitBorder = Color(0xFF585856)
    val PadMuteLitLetter = Color(0xFF6D6D6A)

    /** Chrome. */
    val RowNumber = Color(0xFF5C5C5A)
    val Guide = Color(0x12FFFFFF)
    val ButtonShadow = Color(0xFF0A0A0A)
    val ButtonShadowLit = Color(0xFF6B1414)
    val Scrim = Color(0x73000000)

    /** Light-background logo variant, kept for external materials. */
    val LightBg = Color(0xFFF4F3EF)
    val LightWord = Color(0xFF1A1A19)
    val LightPadBody = Color(0xFFE6E4DE)
    val LightPadLine = Color(0xFFC9C6BD)
    val LightPadLetter = Color(0xFF6E6C66)
}

/** Geometry shared by pad, logo and icon. */
object RudiDimens {
    /** Pad corner radius as a fraction of its side (right hand). */
    const val PAD_CORNER_FRACTION = 0.22f

    /** LED diameter as a fraction of the pad side. */
    const val PAD_LED_FRACTION = 0.12f

    /** LED inset for the square pad: top 11% / right 13%. */
    const val PAD_LED_TOP_SQUARE = 0.11f
    const val PAD_LED_RIGHT_SQUARE = 0.13f

    /** LED inset for the round pad, pushed inwards along the diagonal. */
    const val PAD_LED_TOP_ROUND = 0.18f
    const val PAD_LED_RIGHT_ROUND = 0.19f

    /** Hand letter size inside a pad (brandbook: 0.42 * S). */
    const val PAD_LETTER_FRACTION = 0.42f

    /** Smaller letters read better on the dense metronome grid. */
    const val PAD_LETTER_FRACTION_GRID = 0.32f

    val PadBorder: Dp = 1.dp
    val PadMinSize: Dp = 16.dp
    val PadActiveMaxSize: Dp = 62.dp
    val PadNeighbourMaxSize: Dp = 46.dp

    val DrumSlotHeight: Dp = 92.dp
    val RowNumberWidth: Dp = 20.dp

    val CardCorner: Dp = 18.dp
    val SheetCorner: Dp = 20.dp
    val StepperButtonCorner: Dp = 8.dp

    val LabelTracking = 0.18f.sp
}

/** Motion constants shared by the drum and the pads. */
object RudiMotion {
    const val SPIN_MIN_MS = 90
    const val SPIN_MAX_MS = 280
    const val SPIN_RATIO = 0.8

    const val LONG_PRESS_MS = 420L

    const val NEIGHBOUR_ALPHA = 0.4f
    const val NEIGHBOUR_SCALE = 0.85f

    /** Drum rotation duration derived from tempo, always shorter than a beat. */
    fun spinMillis(bpm: Int): Int {
        val beatMs = 60_000.0 / bpm.coerceAtLeast(1)
        return (beatMs * SPIN_RATIO).coerceIn(SPIN_MIN_MS.toDouble(), SPIN_MAX_MS.toDouble())
            .toInt()
    }
}
