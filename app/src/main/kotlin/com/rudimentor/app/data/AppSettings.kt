package com.rudimentor.app.data

import com.rudimentor.app.audio.BeatPattern
import com.rudimentor.app.audio.Hand
import com.rudimentor.app.ui.BeatIndicatorStyle
import com.rudimentor.app.ui.theme.PaletteId

enum class PatternMode {
    Abstract,
    RightLeft,
}

data class AppSettings(
    val trackerStyle: BeatIndicatorStyle = BeatIndicatorStyle.Default,
    val paletteId: PaletteId = PaletteId.Default,
    val mode: PatternMode = PatternMode.RightLeft,
    val pattern: BeatPattern = BeatPattern.default(),
)

internal fun parseSettings(
    trackerStyle: String?,
    paletteId: String?,
    mode: String?,
    patternLength: Int?,
    accents: String?,
    hands: String?,
): AppSettings {
    val length = patternLength?.coerceIn(BeatPattern.MIN_BEATS, BeatPattern.MAX_BEATS)
        ?: BeatPattern.MIN_BEATS
    val default = BeatPattern.default().resized(length)
    val parsedAccents = accents
        ?.takeIf { it.length == length && it.all { value -> value == '0' || value == '1' } }
        ?.map { it == '1' }
        ?: default.accents
    val parsedHands = hands
        ?.takeIf { it.length == length && it.all { value -> value == 'R' || value == 'L' } }
        ?.map { if (it == 'R') Hand.Right else Hand.Left }
        ?: default.hands

    return AppSettings(
        trackerStyle = BeatIndicatorStyle.fromSavedValue(trackerStyle),
        paletteId = PaletteId.fromSavedValue(paletteId),
        mode = PatternMode.entries.firstOrNull { it.name == mode } ?: PatternMode.RightLeft,
        pattern = BeatPattern(accents = parsedAccents, hands = parsedHands),
    )
}

internal fun BeatPattern.serializedAccents(): String = accents.joinToString(separator = "") {
    if (it) "1" else "0"
}

internal fun BeatPattern.serializedHands(): String = hands.joinToString(separator = "") { hand ->
    hand.label
}
