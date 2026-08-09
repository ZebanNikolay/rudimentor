package com.rudimentor.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatPatternTest {
    @Test
    fun `default pattern accents only the first beat`() {
        val pattern = BeatPattern.default()

        assertEquals(4, pattern.size)
        assertEquals(1, pattern.accentMask)
        assertEquals(10, pattern.leftHandMask)
        assertEquals(listOf(true, false, false, false), pattern.accents)
        assertEquals(listOf(Hand.Right, Hand.Left, Hand.Right, Hand.Left), pattern.hands)
    }

    @Test
    fun `hands toggle independently into all-right and all-left sequences`() {
        val allRight = BeatPattern.default()
            .toggleHand(1)
            .toggleHand(3)
        val allLeft = allRight.hands.indices.fold(allRight) { pattern, index ->
            pattern.toggleHand(index)
        }

        assertEquals(List(4) { Hand.Right }, allRight.hands)
        assertEquals(List(4) { Hand.Left }, allLeft.hands)
        assertEquals(listOf(true, false, false, false), allLeft.accents)
    }

    @Test
    fun `changing one hand leaves every other beat unchanged`() {
        val pattern = BeatPattern.default().toggleHand(2)

        assertEquals(listOf(Hand.Right, Hand.Left, Hand.Left, Hand.Left), pattern.hands)
    }

    @Test
    fun `toggle changes the selected accent and mask`() {
        val pattern = BeatPattern.default().toggleAccent(0).toggleAccent(2)

        assertFalse(pattern.accents[0])
        assertTrue(pattern.accents[2])
        assertEquals(4, pattern.accentMask)
    }

    @Test
    fun `beat count stays within four and eight beats`() {
        val minimum = BeatPattern.default()
        assertSame(minimum, minimum.removeBeat())

        val maximum = generateSequence(minimum) { it.addBeat() }
            .first { it.size == BeatPattern.MAX_BEATS }

        assertEquals(8, maximum.size)
        assertEquals(
            listOf(
                Hand.Right,
                Hand.Left,
                Hand.Right,
                Hand.Left,
                Hand.Right,
                Hand.Left,
                Hand.Right,
                Hand.Left,
            ),
            maximum.hands,
        )
        assertSame(maximum, maximum.addBeat())
        val shortened = maximum.removeBeat()
        assertEquals(7, shortened.size)
        assertEquals(7, shortened.hands.size)
    }

    @Test
    fun `resizing preserves every still-valid customized beat`() {
        val customized = BeatPattern.default()
            .toggleAccent(2)
            .toggleHand(1)
            .resized(8)
            .toggleHand(6)

        val restored = customized.resized(5).resized(8)

        assertEquals(customized.accents.take(5), restored.accents.take(5))
        assertEquals(customized.hands.take(5), restored.hands.take(5))
        assertEquals(Hand.Right, restored.hands[1])
    }
}
