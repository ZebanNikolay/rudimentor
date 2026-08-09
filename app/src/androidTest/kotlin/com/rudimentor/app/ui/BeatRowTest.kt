package com.rudimentor.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.rudimentor.app.audio.BeatPattern
import com.rudimentor.app.data.PatternMode
import com.rudimentor.app.ui.theme.RudiMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BeatRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun eightBeatRowKeepsEverySemanticTouchTarget() {
        val clickedBeats = mutableListOf<Int>()
        composeRule.setContent {
            RudiMentorTheme {
                BeatRow(
                    pattern = BeatPattern.default().resized(8),
                    style = BeatIndicatorStyle.RoundSquare,
                    mode = PatternMode.RightLeft,
                    activeBeat = -1,
                    onBeatClick = clickedBeats::add,
                )
            }
        }

        repeat(8) { index ->
            composeRule.onNodeWithContentDescription("Beat ${index + 1}", substring = true)
                .performClick()
        }
        assertEquals((0..7).toList(), clickedBeats)
    }
}
