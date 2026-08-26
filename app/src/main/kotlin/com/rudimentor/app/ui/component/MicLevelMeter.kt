package com.rudimentor.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rudimentor.app.audio.MicThreshold
import com.rudimentor.app.ui.theme.RudiColors

/**
 * The microphone, drawn on a scale where the room and a stroke are both visible.
 *
 * The practice HUD meter is linear, and that is why the noise the detector was scoring in
 * the dev.37 log was invisible: an envelope of 0.012 is under one pixel of a 74 dp bar,
 * while a stroke fills the whole bar. This meter is logarithmic, -60 dB to full scale, so
 * the room sits near the left edge instead of on it, the gate mark sits between the two,
 * and the learner can see what they are setting (decision 158).
 *
 * Three things are drawn: the live envelope, the decaying peak of the loudest recent
 * stroke, and the gate. Everything under the gate is drawn muted -- it is what the app
 * will ignore.
 */
@Composable
fun MicLevelMeter(
    envelope: Float,
    peak: Float,
    thresholdLevel: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val gateFraction = MicThreshold.toFraction(thresholdLevel)
    val liveFraction = MicThreshold.toFraction(envelope)
    val peakFraction = MicThreshold.toFraction(peak)
    val base = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Box(modifier = base.fillMaxWidth().height(METER_HEIGHT)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = RudiColors.SurfaceAlt, cornerRadius = radius)

            if (liveFraction > 0f) {
                drawRoundRect(
                    color = if (envelope >= thresholdLevel && thresholdLevel > 0f) {
                        RudiColors.BrickLit
                    } else {
                        RudiColors.Line
                    },
                    size = Size(size.width * liveFraction, size.height),
                    cornerRadius = radius,
                )
            }

            // The loudest stroke of the last few seconds: the learner needs to see how
            // far above the gate their playing actually lands.
            if (peakFraction > 0f) {
                val x = (size.width * peakFraction).coerceIn(0f, size.width - 2f)
                drawLine(
                    color = RudiColors.Text,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 4f,
                )
            }

            if (gateFraction > 0f) {
                val x = (size.width * gateFraction).coerceIn(0f, size.width - 2f)
                drawLine(
                    color = RudiColors.Brick,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 6f,
                )
            }
        }
    }
}

private val METER_HEIGHT = 18.dp
