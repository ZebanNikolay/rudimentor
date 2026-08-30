package com.rudimentor.app.audio

/**
 * The audio path as two numbers instead of one, and the rule that decides where each of
 * them goes (decision 188).
 *
 * The hit line on the track is "now". Three things have to meet on it for a stroke that is
 * on time: the stroke as the microphone heard it, the picture of the note, and the click in
 * the headphones. That gives two independent equations:
 *
 *  1. the stroke lands under the line when the compensation taken off it equals the input
 *     half plus however far the picture is drawn ahead;
 *  2. the click sounds while the note is on the line when the picture is drawn ahead by
 *     exactly the output half.
 *
 * Together they say the compensation is the whole round trip -- which is what the
 * calibration measures and what the engine already subtracts -- but only while the picture
 * carries the output half. It did not: the picture was drawn on whatever
 * `calculateLatencyMillis()` reported, and over A2DP this phone reports 4 ms. So a player
 * following the picture was judged a whole output latency early -- the dev.48 field log
 * scored a run of 39 near-perfect strokes as 0.0 % (see docs/research/latency).
 *
 * The measurement can only produce the sum, so the split is a second measurement: the input
 * stream reports its own latency honestly, because that path is local hardware.
 */
object LatencyModel {

    /**
     * Input half used when the stream reports none: microphone buffer plus onset detection.
     * The field estimate from two runs of the same level played to two different references
     * -- once by ear, once by eye -- was 38 ms on the Pixel 10 Pro Fold.
     */
    const val DEFAULT_MIC_MS = 25f

    /**
     * Above this a reported input latency is not believed. The microphone path is local
     * hardware and cannot take a fifth of a second; a number that big means the stream
     * reported the wrong thing, and trusting it would eat the whole output half.
     */
    const val MAX_MIC_MS = 200f

    /**
     * The half of [roundTripMs] that belongs to the stroke, in milliseconds.
     *
     * [measuredMs] is what the input stream reports, or 0 when it reports nothing. The
     * result never exceeds the round trip: the output half must not go negative.
     */
    fun micPart(measuredMs: Float, roundTripMs: Float): Float {
        val believable = measuredMs.isFinite() && measuredMs > 0f && measuredMs <= MAX_MIC_MS
        val candidate = if (believable) measuredMs else DEFAULT_MIC_MS
        return candidate.coerceIn(0f, roundTripMs.coerceAtLeast(0f))
    }

    /**
     * The half of [roundTripMs] that belongs to the picture and to the click: what is left
     * once the stroke's half is taken out.
     */
    fun outputPart(roundTripMs: Float, micMs: Float): Float =
        (roundTripMs - micMs).coerceAtLeast(0f)
}
