package com.rudimentor.app.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.rudimentor.app.ui.component.padPalette
import com.rudimentor.app.ui.theme.JetBrainsMono
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The scrolling note track: notes are pads, drawn right to left onto the hit line.
 *
 * Everything is painted in one Canvas through [drawPadFace], the same face the
 * [com.rudimentor.app.ui.component.Pad] composable uses, so a hundred notes cost
 * one draw pass instead of a hundred composables while still looking identical to
 * the metronome pads.
 *
 * [frame] is the poll counter: reading it inside the draw lambda is what makes the
 * track redraw on every engine poll.
 */
@Composable
fun PracticeTrack(
    notes: List<PracticeNote>,
    attempt: PracticeAttempt,
    positionMs: Float,
    beatMs: Float,
    frame: Int,
    modifier: Modifier = Modifier,
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
        val side = minOf(with(density) { NOTE_SIDE.toPx() }, height * 0.34f)
        val noteCenterY = height * 0.44f
        val baselineY = noteCenterY + side * 0.72f + with(density) { 9.dp.toPx() }

        drawBarLines(
            positionMs = positionMs,
            beatMs = beatMs,
            pxPerMs = pxPerMs,
            lineX = lineX,
            height = height,
        )
        drawHitZone(
            lineX = lineX,
            pxPerMs = pxPerMs,
            top = noteCenterY - side * 0.9f,
            bottom = baselineY,
        )
        drawHitLine(lineX = lineX, height = height)
        drawBaseline(baselineY = baselineY, width = width)

        val letterSize = with(density) { (side * 0.42f).toSp() }
        val letterStyle = TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = letterSize,
        )

        drawCountIn(
            positionMs = positionMs,
            beatMs = beatMs,
            pxPerMs = pxPerMs,
            lineX = lineX,
            noteCenterY = noteCenterY,
            side = side,
            measurer = measurer,
            style = letterStyle,
        )

        notes.forEach { note ->
            val x = lineX + (note.timeMs - positionMs) * pxPerMs
            if (x < -side || x > width + side) return@forEach
            val judgement = attempt.judgementAt(note.index)
            val missed = judgement?.window == HitWindow.Miss
            // A missed note drops out of the lane and fades instead of being crossed
            // out (decision 87).
            val fallProgress = if (missed) {
                ((positionMs - (note.timeMs + PracticeScoring.OK_MS)) / MISS_FALL_MS)
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            val fallY = fallProgress * fallProgress * side * 1.6f
            val alpha = if (missed) (1f - fallProgress * 0.85f).coerceAtLeast(0.15f) else 1f
            val lit = judgement != null && judgement.window != HitWindow.Miss
            val palette = padPalette(
                round = note.hand == PatternHand.Left,
                tone = PadTone.Normal,
                lit = lit,
                light = false,
            )
            drawPadFace(
                topLeft = Offset(x - side / 2f, noteCenterY - side / 2f + fallY),
                side = side,
                round = note.hand == PatternHand.Left,
                tone = PadTone.Normal,
                palette = palette,
                lit = lit,
                strokeWidth = with(density) { 1.dp.toPx() },
                light = false,
                alpha = palette.alpha * alpha,
            )
            val label = measurer.measure(
                text = if (note.hand == PatternHand.Right) "R" else "L",
                style = letterStyle.copy(color = palette.letter.copy(alpha = alpha)),
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x - label.size.width / 2f,
                    noteCenterY - label.size.height / 2f + fallY,
                ),
            )

            // The hit itself is a dot on the baseline, offset by how late or early it
            // landed -- the note keeps its own place on the grid.
            if (judgement != null && judgement.window != HitWindow.Miss) {
                drawCircle(
                    color = windowColor(judgement.window),
                    radius = with(density) { HIT_DOT.toPx() } / 2f,
                    center = Offset(x + judgement.offsetMs * pxPerMs, baselineY),
                )
            }
        }

        attempt.extras.forEach { hitMs ->
            val x = lineX + (hitMs - positionMs) * pxPerMs
            if (x < 0f || x > width) return@forEach
            drawCircle(
                color = RudiColors.TrackExtraHit.copy(alpha = 0.7f),
                radius = with(density) { HIT_DOT.toPx() } / 2f,
                center = Offset(x, baselineY),
            )
        }

        // The verdict of the last judged note sits above the hit line, always in
        // milliseconds (decision 86).
        val judged = attempt.lastJudged
        val verdict = verdictText(judged)
        if (verdict != null) {
            val layout = measurer.measure(
                text = verdict,
                style = letterStyle.copy(
                    fontSize = with(density) { (side * 0.30f).toSp() },
                    color = windowColor(judged?.window ?: HitWindow.Miss),
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    lineX - layout.size.width / 2f,
                    noteCenterY - side * 0.85f - layout.size.height,
                ),
            )
        }
    }
}

internal fun windowColor(window: HitWindow): Color = when (window) {
    HitWindow.Perfect -> RudiColors.WindowPerfect
    HitWindow.Good -> RudiColors.WindowGood
    HitWindow.Ok -> RudiColors.WindowOk
    HitWindow.Miss -> RudiColors.WindowMiss
}

/** Bar lines every four beats, plus a quieter line on every beat. */
private fun DrawScope.drawBarLines(
    positionMs: Float,
    beatMs: Float,
    pxPerMs: Float,
    lineX: Float,
    height: Float,
) {
    if (beatMs <= 0f) return
    val firstBeat = ((positionMs - lineX / pxPerMs) / beatMs).toInt() - 1
    val lastBeat = ((positionMs + (size.width - lineX) / pxPerMs) / beatMs).toInt() + 1
    for (beat in firstBeat..lastBeat) {
        val x = lineX + (beat * beatMs - positionMs) * pxPerMs
        if (x < 0f || x > size.width) continue
        val strong = beat % 4 == 0
        drawLine(
            color = if (strong) RudiColors.TrackBarStrong else RudiColors.TrackBar,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f,
        )
    }
}

/** The brick gradient behind the hit line, as wide as the OK window. */
private fun DrawScope.drawHitZone(lineX: Float, pxPerMs: Float, top: Float, bottom: Float) {
    val halfWidth = PracticeScoring.OK_MS * pxPerMs
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to RudiColors.Brick.copy(alpha = 0.22f),
            1f to Color.Transparent,
            startX = lineX - halfWidth,
            endX = lineX + halfWidth,
        ),
        topLeft = Offset(lineX - halfWidth, top),
        size = Size(halfWidth * 2f, bottom - top),
    )
}

/** The hit line is shortened to 21..79% of the height and capped with square nibs. */
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

/** The thin rail the hit dots sit on. */
private fun DrawScope.drawBaseline(baselineY: Float, width: Float) {
    drawLine(
        color = RudiColors.TrackHitLine.copy(alpha = 0.45f),
        start = Offset(0f, baselineY),
        end = Offset(width, baselineY),
        strokeWidth = 1f,
    )
}

/**
 * The count-in rides along the track like the notes do: four digits with a tick,
 * lit once they pass the hit line, and the microphone is not judged yet.
 */
private fun DrawScope.drawCountIn(
    positionMs: Float,
    beatMs: Float,
    pxPerMs: Float,
    lineX: Float,
    noteCenterY: Float,
    side: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    for (beat in 0 until PracticeScoring.COUNT_IN_BEATS) {
        val timeMs = beat * beatMs
        val x = lineX + (timeMs - positionMs) * pxPerMs
        if (x < -side || x > size.width + side) continue
        val passed = positionMs >= timeMs
        val color = if (passed) RudiColors.PadLedLit else RudiColors.Muted
        val digit = measurer.measure(
            text = (beat + 1).toString(),
            style = style.copy(color = color),
        )
        drawText(
            textLayoutResult = digit,
            topLeft = Offset(x - digit.size.width / 2f, noteCenterY - digit.size.height / 2f),
        )
        drawLine(
            color = color.copy(alpha = 0.6f),
            start = Offset(x, noteCenterY + side * 0.55f),
            end = Offset(x, noteCenterY + side * 0.85f),
            strokeWidth = 2f,
        )
    }
}

/** Verdict text of the last judged note, always in milliseconds. */
internal fun verdictText(judgement: NoteJudgement?): String? = when {
    judgement == null -> null
    judgement.window == HitWindow.Miss -> "MISS"
    else -> PracticeScoring.verdictLabel(judgement.offsetMs)
}

private const val VISIBLE_MS = 2000f
private const val LINE_FRACTION = 0.33f
private const val MISS_FALL_MS = 420f
private val NOTE_SIDE = 44.dp
private val HIT_DOT = 11.dp
