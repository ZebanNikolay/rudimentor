package com.rudimentor.app.ui.util

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import com.rudimentor.app.util.AppLog
import kotlinx.coroutines.delay

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
 *
 * The window flag did not end it either, and the first log with it (dev.61, 2026-10-13)
 * showed the shape of the failure: the flag was on for the whole attempt, yet the display
 * dimmed and locked about 30 s -- the lock timeout -- after the start, and only in attempts
 * started from the level card, where the stage turns the phone to landscape in the same
 * frame. Attempts restarted from the result screen, already in landscape, ran a minute and
 * more. The same phone on dev.59, with the request on the view, never lost one. So the
 * flag the window holds survived, but the one the window manager acts on did not live
 * through the rotation.
 *
 * Now the request is made both ways and kept up (decision 233):
 * - the window flag, as before, and `keepScreenOn` on the decor view, which every
 *   traversal folds back into the window it reports -- that is what held on dev.59;
 * - after every configuration change the attributes are sent to the window manager again,
 *   once the rotation has settled;
 * - owners are counted per window, because the helper has two callers -- the app-level
 *   stage and the metronome -- and whichever let go first used to clear it under the other;
 * - while an owner is active the helper checks both every few seconds, puts back whatever
 *   is missing and writes `keep-awake lost` to the log.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val activity = LocalActivity.current
    val window = activity?.window
    val configuration = LocalConfiguration.current

    DisposableEffect(window, active) {
        if (window == null || !active) return@DisposableEffect onDispose { }
        ScreenAwakeOwners.acquire(window)
        onDispose { ScreenAwakeOwners.release(window) }
    }

    // A rotation is exactly what lost the request on dev.61: hand it to the window manager
    // again once the new configuration has been laid out.
    LaunchedEffect(window, active, configuration.orientation, configuration.screenWidthDp) {
        if (window == null || !active) return@LaunchedEffect
        delay(SETTLE_AFTER_CONFIG_MS)
        ScreenAwakeOwners.reassert(window, reason = "config")
    }

    LaunchedEffect(window, active) {
        if (window == null || !active) return@LaunchedEffect
        while (true) {
            delay(WATCH_PERIOD_MS)
            ScreenAwakeOwners.restoreIfLost(window)
        }
    }

    OnForegrounded {
        if (window == null || !active) return@OnForegrounded
        ScreenAwakeOwners.reassert(window, reason = "resume")
    }
}

/** How many active callers want each window awake: the request goes with the last of them. */
private object ScreenAwakeOwners {
    private val counts = mutableMapOf<Window, Int>()

    fun acquire(window: Window) {
        val count = (counts[window] ?: 0) + 1
        counts[window] = count
        AppLog.trace("screen") { "keep-awake on ($count)" }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = true
    }

    fun release(window: Window) {
        val count = ((counts[window] ?: 1) - 1).coerceAtLeast(0)
        AppLog.trace("screen") { "keep-awake off ($count)" }
        if (count == 0) {
            counts.remove(window)
            window.decorView.keepScreenOn = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            counts[window] = count
        }
    }

    /** Sends the request to the window manager again, whether or not it looks lost. */
    fun reassert(window: Window, reason: String) {
        if ((counts[window] ?: 0) == 0) return
        AppLog.trace("screen") { "keep-awake reassert ($reason) ${state(window)}" }
        window.decorView.keepScreenOn = true
        // addFlags dispatches the attributes even when the bit is already set.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun restoreIfLost(window: Window) {
        if ((counts[window] ?: 0) == 0) return
        val flag = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        val view = window.decorView.keepScreenOn
        if (flag && view) return
        AppLog.error("screen", "keep-awake lost, restored (${state(window)})")
        window.decorView.keepScreenOn = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun state(window: Window): String {
        val flag = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        return "flag=$flag view=${window.decorView.keepScreenOn}"
    }
}

private const val SETTLE_AFTER_CONFIG_MS = 600L
private const val WATCH_PERIOD_MS = 5_000L
