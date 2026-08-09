package com.rudimentor.app.ui

enum class BeatIndicatorStyle(
    val number: Int,
    val displayName: String,
    val source: String,
) {
    RoundSquare(1, "Round ↔ Square Toggle", "Material 3 Button Groups"),
    HalfFilledDot(2, "Half-Filled Direction Dot", "Carbon Progress Indicator"),
    CaretChip(3, "Caret Direction Chip", "Carbon + Radix Toggle Group"),
    PolygonMorph(4, "Polygon Morph Beat", "Material 3 + AndroidX Shapes"),
    WavyTrack(5, "Wavy vs Flat Track", "Material 3 Progress Indicators"),
    RingSweep(6, "Ring Sweep CW / CCW", "Spectrum + Apple HIG"),
    AlphaStep(7, "Alpha-Step Fill", "Spectrum Color System"),
    SpikeColumn(8, "Spike Column", "compose-audiowaveform"),
    IndicatorGlyph(9, "Indicator-Slot Glyph", "Radix + Material 3 Radio"),
    NumberedRail(10, "Numbered Step Rail", "Carbon + Material 3"),
    ;

    companion object {
        val Default = RoundSquare

        fun fromSavedValue(value: String?): BeatIndicatorStyle =
            entries.firstOrNull { it.name == value } ?: Default
    }
}
