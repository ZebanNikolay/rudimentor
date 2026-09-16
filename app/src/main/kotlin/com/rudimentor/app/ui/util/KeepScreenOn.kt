package com.rudimentor.app.ui.util

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.rudimentor.app.util.AppLog

/**
 * Keeps the display awake while [active] is true.
 *
 * Every screen that plays -- an attempt, the metronome, the calibration and the sound
 * check -- goes through this one helper on purpose (decision 198). Before it the app had
 * two owners of the same request: the practice stage set `FLAG_KEEP_SCREEN_ON` on the
 * window by hand while the metronome set `View.keepScreenOn`. Those are not two
 * independent switches: `View.keepScreenOn` makes the view hierarchy the authority over
 * that window flag, so the next layout traversal after the metronome released it
 * recomputed the flag from a tree where nothing asked for it and cleared the practice
 * stage's flag with it.
 *
 * Having one owner fixed the collision but left the request on the weaker of the two
 * mechanisms, and the display still went out mid-attempt now and then. A view-held
 * request is not a stored setting: the window rebuilds it from the tree on traversals,
 * and it only survives as long as the view that asks for it is attached and drawn. The
 * practice flow rebuilds its tree under the running attempt -- the stage swaps screens
 * and the orientation change is handled in place -- so a traversal that ran while the
 * asking view was out of the tree dropped the request, and a released display times out
 * and takes the audio streams with it (decision 158). It was rare because it needed that
 * traversal to land in the gap (decision 222).
 *
 * The request now lives on the window itself, where it is a flag that stays set until
 * this helper clears it, and it is re-asserted on resume: returning from the background
 * already loses the immersive bars, and the flag is lost the same way.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val activity = LocalActivity.current
    val window = activity?.window

    DisposableEffect(window, active) {
        if (window == null || !active) return@DisposableEffect onDispose { }
        AppLog.trace("screen") { "keep-awake on" }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            AppLog.trace("screen") { "keep-awake off" }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    OnForegrounded {
        if (window == null || !active) return@OnForegrounded
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
