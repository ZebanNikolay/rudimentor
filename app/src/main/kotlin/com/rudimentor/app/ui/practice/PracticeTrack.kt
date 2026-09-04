package com.rudimentor.app.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.rudimentor.app.data.levels.PatternHand
import com.rudimentor.app.ui.component.PadTone
import com.rudimentor.app.ui.component.drawPadFace
import com.rudimentor.app.ui.component.drawPadGlow
import com.rudimentor.app.ui.component.padPalette
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors
import com.rudimentor.app.ui.theme.RudiDimens

/**
 * The scrolling note track: notes are pads, drawn right to left onto the hit line.
 *
 * Everything is painted in one Canvas through [drawPadFace], the same face the
 * [com.rudimentor.app.ui.component.Pad] composable uses, so a hundred notes cost
 * one draw pass instead of a hundred composables while still looking identical to
 * the metronome pads.
 *
 * A note has two looks only: ahead of the hit line it wears the accent frame, and
 * once it is judged it falls back to the plain, dimmed face (decision 115). The
 * accent tone is free for this because no lesson marks accented strokes yet -- when
 * they arrive they will get a look of their own.
 *
 * Geometry follows the approved concept: one lane line across the vertical centre
 * with the notes sitting on it and hit dots on a virtual rail below the pads. The
 * verdict of a stroke is written under its own note, in the colour of its window, and
 * rides away with the note instead of flying over the hit line (decision 143): a word
 * that climbs over the hit line collides with the line itself and is gone before it can
 * be read, while a word parked under the note leaves a readable trail of the attempt.
 *
 * The note side comes from the pad size library ([RudiDimens.TrackNoteSize]) and is
 * read from arm's length over a practice pad, which is also why the lane shows less
 * time at once than the concept did: the notes have to keep their air.
 *
 * [frame] is the poll counter: reading it inside the draw lambda is what makes the
 * track redraw on every engine poll.
 */
@Composable
fun PracticeTrack(
    notes: List<PracticeNote>,
    attempt: PracticeAttempt,
    positionMs: Float,
    beatTimesMs: FloatArray,
    countInBeats: Int,
    frame: Int,
    finishMs: Float,
    modifier: Modifier = Modifier,
    showOffsetMs: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        // Reading the poll counter is what makes the track redraw every poll.
        if (frame < 0) return@Canvas

        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        val pxPerMs = width / VISIBLE_MS
        val lineX = width * LINE_FRACTION
        val side = minOf(
            with(density) { RudiDimens.TrackNoteSize.toPx() },
            height * RudiDimens.TRACK_NOTE_HEIGHT_FRACTION,
        )
        // The lane sits on the vertical centre and the notes are centred on it.
        val laneY = height / 2f
        val hitDotY = laneY + side * HIT_DOT_OFFSET
        val extraDotY = laneY + side * EXTRA_DOT_OFFSET
        // The verdict block starts below the hit dot rail and stays inside the canvas.
        val verdictY = minOf(laneY + side * VERDICT_TOP_OFFSET, height - side * 0.5f)

        drawBarLines(
            positionMs = positionMs,
            beatTimesMs = beatTimesMs,
            pxPerMs = pxPerMs,
            lineX = lineX,
            height = height,
            // Bars stop at the finish: the click stops there too, so a lane that kept
            // ruling out beats past the end was keeping time to nothing (decision 203).
            untilMs = finishMs,
        )
        drawLane(laneY = laneY, width = width)
        drawHitLine(lineX = lineX, height = height)

        val letterSize = with(density) { (side * 0.42f).toSp() }
        val letterStyle = TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = letterSize,
        )

        drawCountIn(
            positionMs = positionMs,
            beatTimesMs = beatTimesMs,
            countInBeats = countInBeats,
            pxPerMs = pxPerMs,
            lineX = lineX,
            laneY = laneY,
            side = side,
            measurer = measurer,
            style = letterStyle,
        )

        // The finish is a line across the lane, not a pad: a pad shape says "hit me",
        // and on the first live run it did exactly that (decision 130 replaces 116).
        // Drawn before the notes so the last note passes over it, not under it.
        if (finishMs > 0f) {
            val finishX = lineX + (finishMs - positionMs) * pxPerMs
            if (finishX > -side && finishX < width + side) {
                drawFinishLine(
                    x = finishX,
                    height = height,
                    passed = positionMs >= finishMs,
                    strokeWidth = with(density) { FINISH_STROKE.toPx() },
                    measurer = measurer,
                    style = letterStyle.copy(
                        fontSize = with(density) { (side * FINISH_LABEL_FRACTION).toSp() },
                    ),
                )
            }
        }

        notes.forEach { note ->
            val x = lineX + (note.timeMs - positionMs) * pxPerMs
            if (x < -side || x > width + side) return@forEach
            // No mark in front of a new block: the brick hairline of decisions 141/146 was
            // read on the device as one more grid line and only muddied the lane, and the
            // switch is legible from the letters themselves (decision 196).
            val judgement = attempt.judgementAt(note.index)
            val missed = judgement?.window == HitWindow.Miss
            // A missed note drops out of the lane and fades instead of being crossed
            // out (decision 87).
            val fallProgress = if (missed) {
                ((positionMs - (note.timeMs + attempt.windows.forNote(note.index).okMs)) / MISS_FALL_MS)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val fallY = fallProgress * fallProgress * side * 1.6f
            val alpha = if (missed) (1f - fallProgress * 0.85f).coerceAtLeast(0.15f) else 1f
            // Three looks, one per state of the stroke (decision 197, replaces 115): a note
            // still coming wears the accent frame, a note that was hit *lights up* -- the same
            // brick fill and halo a struck pad has on the metronome -- and a missed one keeps
            // the plain dimmed face while it drops out of the lane. Lighting the hit is what
            // says "that one landed" at a glance; the old dimmed face read as disabled.
            val played = judgement != null
            val lit = played && !missed
            val tone = if (played) PadTone.Normal else PadTone.Accent
            val palette = padPalette(
                round = note.hand == PatternHand.Left,
                tone = tone,
                lit = lit,
                light = false,
            )
            val faceAlpha = palette.alpha * alpha
            if (lit) {
                drawPadGlow(
                    center = Offset(x, laneY),
                    side = side,
                    accent = false,
                    alpha = faceAlpha,
                )
            }
            drawPadFace(
                topLeft = Offset(x - side / 2f, laneY - side / 2f + fallY),
                side = side,
                round = note.hand == PatternHand.Left,
                tone = tone,
                palette = palette,
                lit = lit,
                strokeWidth = with(density) { 1.dp.toPx() },
                light = false,
                alpha = faceAlpha,
            )
            val label = measurer.measure(
                text = if (note.hand == PatternHand.Right) "R" else "L",
                style = letterStyle.copy(color = palette.letter.copy(alpha = alpha)),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x - label.size.width / 2f,
                    laneY - label.size.height / 2f + fallY,
                ),
            )

            // The hit itself is a dot below the note, offset by how late or early it
            // landed -- the note keeps its own place on the grid.
            if (judgement != null && judgement.window != HitWindow.Miss) {
                drawCircle(
                    color = windowColor(judgement.window),
                    radius = side * HIT_DOT_SIZE / 2f,
                    center = Offset(x + judgement.offsetMs * pxPerMs, hitDotY),
                )
            }

            // The verdict sits under the stroke that earned it and scrolls away with it:
            // it never fades and never moves on its own, so a glance back down the lane
            // still reads the last few strokes (decision 143).
            if (judgement != null) {
                val hitX = if (judgement.window == HitWindow.Miss) {
                    x
                } else {
                    x + judgement.offsetMs * pxPerMs
                }
                drawVerdictLabel(
                    x = hitX,
                    topY = verdictY,
                    side = side,
                    judgement = judgement,
                    showOffsetMs = showOffsetMs,
                    measurer = measurer,
                    style = letterStyle,
                )
            }
        }

        attempt.extras.forEach { hitMs ->
            val x = lineX + (hitMs - positionMs) * pxPerMs
            if (x < 0f || x > width) return@forEach
            drawCircle(
                color = RudiColors.TrackExtraHit.copy(alpha = 0.7f),
                radius = side * EXTRA_DOT_SIZE / 2f,
                center = Offset(x, extraDotY),
            )
        }
    }
}

/**
 * The verdict of one stroke: the word of its window under the hit dot, optionally with
 * the milliseconds under it. Bigger than the old parked label (decision 143) because it
 * is read at arm's length over a pad, and drawn at full opacity — the lane scrolls it
 * out on its own, nothing has to fade it.
 */
private fun DrawScope.drawVerdictLabel(
    x: Float,
    topY: Float,
    side: Float,
    judgement: NoteJudgement,
    showOffsetMs: Boolean,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val color = windowColor(judgement.window)
    val word = measurer.measure(
        text = verdictWord(judgement),
        style = style.copy(fontSize = (side * VERDICT_WORD_FRACTION).toSp(), color = color),
    )
    drawText(
        textLayoutResult = word,
        topLeft = Offset(x - word.size.width / 2f, topY),
    )
    if (showOffsetMs && judgement.window != HitWindow.Miss) {
        val offsets = measurer.measure(
            text = PracticeScoring.verdictLabel(judgement.offsetMs),
            style = style.copy(
                fontSize = (side * VERDICT_MS_FRACTION).toSp(),
                color = color.copy(alpha = 0.8f),
            ),
        )
        drawText(
            textLayoutResult = offsets,
            topLeft = Offset(x - offsets.size.width / 2f, topY + word.size.height),
        )
    }
}

/**
 * The end of the lane: one heavy vertical line with FINISH written over it. A pad
 * shape here read as "hit me" and earned an extra stroke on the first live run, so
 * the finish carries no pad geometry at all (decision 130). Once it crosses the hit
 * line it turns brick; LEVEL CLEAR is announced by the HUD, not by the label.
 */
private fun DrawScope.drawFinishLine(
    x: Float,
    height: Float,
    passed: Boolean,
    strokeWidth: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    if (height <= 0f) return
    val top = height * FINISH_TOP_FRACTION
    val bottom = height * (1f - FINISH_TOP_FRACTION)
    drawLine(
        color = if (passed) RudiColors.BrickLit else RudiColors.Text,
        start = Offset(x, top),
        end = Offset(x, bottom),
        strokeWidth = strokeWidth,
    )
    val label = measurer.measure(text = "FINISH", style = style.copy(color = RudiColors.Text))
    drawText(
        textLayoutResult = label,
        topLeft = Offset(
            x - label.size.width / 2f,
            (top - label.size.height - height * FINISH_LABEL_GAP).coerceAtLeast(0f),
        ),
    )
}

internal fun windowColor(window: HitWindow): Color = when (window) {
    HitWindow.Perfect -> RudiColors.WindowPerfect
    HitWindow.Good -> RudiColors.WindowGood
    HitWindow.Ok -> RudiColors.WindowOk
    HitWindow.Miss -> RudiColors.WindowMiss
}

/**
 * Bar lines every four beats, plus a quieter line on every beat. The beats come from the
 * grid of the attempt rather than from one beat length, so the bars of a tempo ramp stay
 * on its clicks (decision 148); beats past the end of the grid keep the last beat length.
 * Nothing is drawn past [untilMs] -- the finish -- because the click is silent by then.
 */
private fun DrawScope.drawBarLines(
    positionMs: Float,
    beatTimesMs: FloatArray,
    pxPerMs: Float,
    lineX: Float,
    height: Float,
    untilMs: Float,
) {
    if (beatTimesMs.isEmpty()) return
    // Widths in dp, not raw pixels: a 1 px line on a ~3x density panel is a third
    // of a hairline and disappeared on device, which is what made the metronome
    // invisible on the track (decision 147).
    val thin = BAR_WIDTH.toPx()
    val thick = BAR_STRONG_WIDTH.toPx()
    val fromMs = positionMs - lineX / pxPerMs
    val toMs = positionMs + (size.width - lineX) / pxPerMs
    val tailBeatMs = if (beatTimesMs.size >= 2) {
        beatTimesMs.last() - beatTimesMs[beatTimesMs.size - 2]
    } else {
        0f
    }
    var beat = 0
    while (true) {
        val timeMs = if (beat < beatTimesMs.size) {
            beatTimesMs[beat]
        } else {
            if (tailBeatMs <= 0f) return
            beatTimesMs.last() + (beat - beatTimesMs.size + 1) * tailBeatMs
        }
        if (timeMs > toMs) return
        if (untilMs > 0f && timeMs > untilMs) return
        if (timeMs < fromMs) {
            beat += 1
            continue
        }
        val x = lineX + (timeMs - positionMs) * pxPerMs
        val barBeat = beat
        beat += 1
        if (x < 0f || x > size.width) continue
        // Every fourth beat is the accented click the engine plays, so it is the
        // one that gets the full-height, brighter bar; the plain beats stay inset
        // so the lane still reads as a lane and not as a grid of equals.
        val strong = barBeat % 4 == 0
        val inset = if (strong) 0f else height * BAR_INSET_FRACTION
        drawLine(
            color = if (strong) RudiColors.TrackBarStrong else RudiColors.TrackBar,
            start = Offset(x, inset),
            end = Offset(x, height - inset),
            strokeWidth = if (strong) thick else thin,
        )
    }
}

/**
 * The hit line is shortened to 21..79% of the height and capped with square nibs.
 * No band behind it: the concept had a faint brick gradient there and on a device it
 * only muddied the lane, so the line carries the target on its own.
 */
private fun DrawScope.drawHitLine(lineX: Float, height: Float) {
    val top = height * 0.21f
    val bottom = height * 0.79f
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX, top),
        end = Offset(lineX, bottom),
        strokeWidth = 2f,
    )
    val nib = 7f
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX - nib / 2f, top),
        end = Offset(lineX + nib / 2f, top),
        strokeWidth = 2f,
    )
    drawLine(
        color = RudiColors.Text,
        start = Offset(lineX - nib / 2f, bottom),
        end = Offset(lineX + nib / 2f, bottom),
        strokeWidth = 2f,
    )
}

/** The lane the notes ride on: one hairline across the vertical centre. */
private fun DrawScope.drawLane(laneY: Float, width: Float) {
    drawLine(
        color = RudiColors.TrackLane,
        start = Offset(0f, laneY),
        end = Offset(width, laneY),
        strokeWidth = 1f,
    )
}

/**
 * The count-in rides along the track like the notes do: four digits with a tick,
 * lit once they pass the hit line, and the microphone is not judged yet.
 */
private fun DrawScope.drawCountIn(
    positionMs: Float,
    beatTimesMs: FloatArray,
    countInBeats: Int,
    pxPerMs: Float,
    lineX: Float,
    laneY: Float,
    side: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    for (beat in 0 until minOf(countInBeats, beatTimesMs.size)) {
        val timeMs = beatTimesMs[beat]
        val x = lineX + (timeMs - positionMs) * pxPerMs
        if (x < -side || x > size.width + side) continue
        val passed = positionMs >= timeMs
        val color = if (passed) RudiColors.PadLedLit else RudiColors.Muted
        val digit = measurer.measure(
            // Two bars of count-in are counted as two bars, not up to eight: the digit
            // says where in the bar the beat is, and "7" says nothing (decision 211).
            text = (beat % PracticeScoring.COUNT_IN_BAR + 1).toString(),
            style = style.copy(color = color),
        )
        val digitCenterY = laneY - side * 0.49f
        drawText(
            textLayoutResult = digit,
            topLeft = Offset(x - digit.size.width / 2f, digitCenterY - digit.size.height / 2f),
        )
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(x, laneY - side * 0.09f),
            end = Offset(x, laneY + side * 0.55f),
            strokeWidth = 2f,
        )
    }
}

/** The word of a window: what is written under the stroke that earned it. */
internal fun verdictWord(judgement: NoteJudgement): String = when (judgement.window) {
    HitWindow.Perfect -> "PERFECT"
    HitWindow.Good -> "GOOD"
    HitWindow.Ok -> "OK"
    HitWindow.Miss -> "MISS"
}

/**
 * How much time the lane shows at once. Shorter than the concept's two seconds: the
 * notes grew by half (decision 130), so the lane has to spread them out to keep the
 * air between them at the fast ranks.
 */
private const val VISIBLE_MS = 1400f
private const val LINE_FRACTION = 0.33f

/** Beat grid strokes: the metronome as seen, in dp so density cannot erase them. */
private val BAR_WIDTH = 1.5.dp
private val BAR_STRONG_WIDTH = 3.dp
private const val BAR_INSET_FRACTION = 0.08f

private const val MISS_FALL_MS = 420f

/** The finish line: how tall it stands, how heavy it is, where its label sits. */
private const val FINISH_TOP_FRACTION = 0.18f
private const val FINISH_LABEL_FRACTION = 0.26f
private const val FINISH_LABEL_GAP = 0.03f
private val FINISH_STROKE = 4.dp

/**
 * The verdict label under a stroke: how big the word is, how big the optional
 * milliseconds are, and how far below the hit dot the block starts. All fractions of the
 * note side, so the label scales with the pads (decision 143).
 */
private const val VERDICT_WORD_FRACTION = 0.40f
private const val VERDICT_MS_FRACTION = 0.20f
private const val VERDICT_TOP_OFFSET = 1.02f

/** Concept geometry, as fractions of the note side (44 px in the concept). */
private const val HIT_DOT_OFFSET = 0.74f
private const val HIT_DOT_SIZE = 0.25f
private const val EXTRA_DOT_OFFSET = 0.58f
private const val EXTRA_DOT_SIZE = 0.16f
