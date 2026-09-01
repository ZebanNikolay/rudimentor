package com.rudimentor.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake while [active] is true.
 *
 * Every screen that plays -- an attempt, the metronome, the calibration and the sound
 * check -- goes through this one helper on purpose (decision 198). Before it the app had
 * two owners of the same flag: the practice stage set `FLAG_KEEP_SCREEN_ON` on the window
 * by hand while the metronome set `View.keepScreenOn`. Those are not two independent
 * switches: `View.keepScreenOn` makes the view hierarchy the authority over that window
 * flag, so the next layout traversal after the metronome released it recomputed the flag
 * from a tree where nothing asked for it and cleared the practice stage's flag with it.
 * The display then dimmed mid-attempt -- the report from the Endurance level -- and a
 * screen that times out takes the audio streams with it (decision 158).
 *
 * One mechanism, one owner: the view flag, released when [active] goes false or the caller
 * leaves the tree.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (!active) return@DisposableEffect onDispose { }
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
