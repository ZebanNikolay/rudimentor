package com.rudimentor.app.ui

enum class BeatIndicatorStyle(val displayName: String) {
    PracticePad("Practice Pad"),
    StepTiles("Step Tiles"),
    MarchingOctagons("Marching Octagons"),
    MetronomeDiamonds("Metronome Diamonds"),
    StickCaps("Stick Caps"),
    ConcentricPulse("Concentric Pulse"),
    ChevronFlow("Chevron Flow"),
    NotchedChips("Notched Chips"),
    RhythmBars("Rhythm Bars"),
    HybridGlyphs("Hybrid Glyphs"),
    ;

    companion object {
        val Default = PracticePad

        fun fromSavedValue(value: String?): BeatIndicatorStyle =
            entries.firstOrNull { it.name == value } ?: Default
    }
}
