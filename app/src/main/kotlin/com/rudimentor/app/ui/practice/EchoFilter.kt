package com.rudimentor.app.ui.practice

/**
 * Drops the weak echo that a stand, a room or the pad itself returns shortly after a stroke.
 *
 * The dev.59 logs (IS-03, EN-03) show it plainly: 45-70 ms after a stroke, sometimes once
 * more around 125 ms, the envelope falls almost to silence and rises again to 7-17 % of the
 * stroke, regardless of the hand and of how hard it was played. The detector is right to call
 * that a new onset; it is just not a stroke. A real second stroke is never like it: the
 * shortest interval in the course is 83 ms and the second stroke of a double is at least
 * half as loud as the first.
 *
 * The rule: an onset weaker than [RATIO] of the loudest accepted onset within the last
 * [WINDOW_MS] is an echo. Echoes never become a reference, so a decaying chain is measured
 * against the stroke that started it. Replayed over all 19 archived attempts it removed 524
 * extras and no stroke that was really played (analysis-2026-10-09).
 *
 * Runs after the detector and before the attempt: the score never sees an echo, the log
 * does. Off when the learner turns it off in settings.
 */
class EchoFilter(val enabled: Boolean) {
    private val times = DoubleArray(CAPACITY)
    private val envelopes = FloatArray(CAPACITY)
    private var size = 0

    /** The echo this onset is, or null when it is a stroke to judge. */
    fun check(atMs: Double, envelope: Float): HitOutcome.Echo? {
        if (!enabled || !atMs.isFinite()) return null
        var keep = 0
        var refEnvelope = 0f
        var refAtMs = Double.NaN
        for (i in 0 until size) {
            if (atMs - times[i] > WINDOW_MS) continue
            times[keep] = times[i]
            envelopes[keep] = envelopes[i]
            if (envelopes[i] > refEnvelope) {
                refEnvelope = envelopes[i]
                refAtMs = times[i]
            }
            keep += 1
        }
        size = keep
        if (envelope.isFinite() && refEnvelope > 0f && envelope < RATIO * refEnvelope) {
            return HitOutcome.Echo(
                gapMs = (atMs - refAtMs).toFloat(),
                ratio = envelope / refEnvelope,
            )
        }
        if (envelope.isFinite() && envelope > 0f) {
            if (size == CAPACITY) {
                System.arraycopy(times, 1, times, 0, CAPACITY - 1)
                System.arraycopy(envelopes, 1, envelopes, 0, CAPACITY - 1)
                size -= 1
            }
            times[size] = atMs
            envelopes[size] = envelope
            size += 1
        }
        return null
    }

    companion object {
        const val WINDOW_MS = 130.0
        const val RATIO = 0.35f

        /** Onsets kept for reference; 130 ms holds far fewer strokes than this. */
        private const val CAPACITY = 16
    }
}
