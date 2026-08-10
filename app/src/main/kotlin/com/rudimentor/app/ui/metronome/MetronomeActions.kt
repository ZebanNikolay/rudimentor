package com.rudimentor.app.ui.metronome

import androidx.compose.runtime.Immutable

/**
 * Callback bundle for [MetronomeScreen]. Grouping the nine independent lambdas
 * in one `@Immutable` holder means the screen only has to close over one
 * reference instead of nine, which keeps recomposition of child composables
 * predictable.
 */
@Immutable
data class MetronomeActions(
    val cycleBeat: (Int, Int) -> Unit,
    val toggleHand: (Int, Int) -> Unit,
    val addBeat: (Int) -> Unit,
    val removeBeat: (Int) -> Unit,
    val addRow: () -> Unit,
    val removeRow: () -> Unit,
    val selectRow: (Int) -> Unit,
    val bpmDelta: (Int) -> Unit,
    val showLettersChange: (Boolean) -> Unit,
)
