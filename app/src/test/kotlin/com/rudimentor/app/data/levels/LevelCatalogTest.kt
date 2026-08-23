package com.rudimentor.app.data.levels

import com.rudimentor.app.audio.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCatalogTest {
    @Test
    fun `rows follow the prerequisite chain`() {
        val catalog = catalog(
            lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02"), lesson("f.ST-03")),
            nodes = listOf(
                node("f.ST-01"),
                node("f.ST-02", prerequisites = setOf("f.ST-01")),
                node("f.ST-03", prerequisites = setOf("f.ST-02")),
            ),
        )

        assertEquals(listOf(0, 1, 2), catalog.levels.map(Level::row))
        assertEquals(2, catalog.lastRow)
        assertEquals(0, catalog.lastLateral)
        assertEquals(listOf("f.ST-01", "f.ST-02", "f.ST-03"), catalog.centerPath.map(Level::id))
    }

    @Test
    fun `side lessons share the row of their branch and take lateral slots per side`() {
        val catalog = catalog(
            lessons = listOf(
                lesson("f.ST-01"),
                lesson("f.ST-02"),
                lesson("f.WK-01"),
                lesson("f.WK-02"),
                lesson("f.EN-01"),
            ),
            nodes = listOf(
                node("f.ST-01"),
                node("f.ST-02", prerequisites = setOf("f.ST-01")),
                node("f.WK-01", column = LevelColumn.Left, prerequisites = setOf("f.ST-02")),
                node("f.WK-02", column = LevelColumn.Left, prerequisites = setOf("f.WK-01")),
                node("f.EN-01", column = LevelColumn.Right, prerequisites = setOf("f.ST-02")),
            ),
        )

        assertEquals(1, catalog.level("f.WK-01")!!.row)
        assertEquals(1, catalog.level("f.WK-02")!!.row)
        assertEquals(1, catalog.level("f.EN-01")!!.row)
        assertEquals(1, catalog.level("f.WK-01")!!.lateral)
        assertEquals(2, catalog.level("f.WK-02")!!.lateral)
        // Sides are counted apart: the first branch on the right also starts at slot one.
        assertEquals(1, catalog.level("f.EN-01")!!.lateral)
        assertEquals(0, catalog.level("f.ST-02")!!.lateral)
        assertEquals(2, catalog.lastLateral)
    }

    @Test
    fun `progress unlocks the required path while optional columns stay independent`() {
        val catalog = catalog(
            lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02"), lesson("f.ST-03"), lesson("f.ST-04")),
            nodes = listOf(
                node("f.ST-01"),
                node("f.ST-02", column = LevelColumn.Left, prerequisites = setOf("f.ST-01")),
                node("f.ST-03", prerequisites = setOf("f.ST-01")),
                node("f.ST-04", prerequisites = setOf("f.ST-03")),
            ),
        )
        val rank = PracticeRank.Practice
        val first = catalog.level("f.ST-01")!!
        val optional = catalog.level("f.ST-02")!!
        val current = catalog.level("f.ST-03")!!
        val future = catalog.level("f.ST-04")!!
        val progress = progress(rank, first.id)

        assertEquals(current, progress.currentLevel(catalog, rank))
        assertEquals(LevelNodeState.Completed, progress.stateOf(first, catalog, rank))
        assertEquals(LevelNodeState.Available, progress.stateOf(optional, catalog, rank))
        assertEquals(LevelNodeState.Current, progress.stateOf(current, catalog, rank))
        assertEquals(LevelNodeState.Locked, progress.stateOf(future, catalog, rank))
        assertFalse(progress.isFamilyComplete(catalog, rank))

        val complete = progress(rank, first.id, current.id, future.id)
        assertTrue(complete.isFamilyComplete(catalog, rank))
    }

    @Test
    fun `each rank walks the map on its own`() {
        val catalog = catalog(
            lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02")),
            nodes = listOf(node("f.ST-01"), node("f.ST-02", prerequisites = setOf("f.ST-01"))),
        )
        val first = catalog.level("f.ST-01")!!
        val second = catalog.level("f.ST-02")!!
        val progress = progress(PracticeRank.Practice, first.id)

        assertEquals(second, progress.currentLevel(catalog, PracticeRank.Practice))
        // Groove has not been played at all, so its own first level is still the first one.
        assertEquals(first, progress.currentLevel(catalog, PracticeRank.Groove))
        assertEquals(LevelNodeState.Locked, progress.stateOf(second, catalog, PracticeRank.Groove))
        assertTrue(progress.forLevel(first.id).startedAnyRank)
        assertFalse(progress.isCompleted(first.id, PracticeRank.Stage))
    }

    @Test
    fun `catalog rejects a row without a required center level`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02")),
                nodes = listOf(node("f.ST-01", column = LevelColumn.Left), node("f.ST-02", column = LevelColumn.Right)),
            )
        }
    }

    @Test
    fun `catalog rejects a side lesson without a branch`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01"), lesson("f.WK-01")),
                nodes = listOf(node("f.ST-01"), node("f.WK-01", column = LevelColumn.Left)),
            )
        }
    }

    @Test
    fun `catalog rejects a lesson outside the family namespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(lessons = listOf(lesson("other.ST-01")), nodes = listOf(node("other.ST-01")))
        }
    }

    @Test
    fun `catalog rejects a prerequisite cycle`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01"), lesson("f.ST-02")),
                nodes = listOf(
                    node("f.ST-01", prerequisites = setOf("f.ST-02")),
                    node("f.ST-02", prerequisites = setOf("f.ST-01")),
                ),
            )
        }
    }

    @Test
    fun `catalog rejects a lesson with both a pattern and a transition plan`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(
                    lesson("f.ST-01").copy(
                        type = LevelType.Transition,
                        transitionPlan = TransitionPlan(
                            repeatCount = 2,
                            phases = listOf(TransitionPhase(beatCount = 8, pattern = pattern("RL"))),
                        ),
                    ),
                ),
                nodes = listOf(node("f.ST-01")),
            )
        }
    }

    @Test
    fun `catalog rejects an execution measured in beats and seconds at once`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(
                    lesson("f.ST-01").copy(
                        execution = Execution(
                            beatCount = 64,
                            durationSeconds = 120,
                            completionMode = CompletionMode.CompletePatternCycle,
                        ),
                    ),
                ),
                nodes = listOf(node("f.ST-01")),
            )
        }
    }

    @Test
    fun `a transition lesson previews its first phase and is not playable`() {
        val level = catalog(
            lessons = listOf(
                Lesson(
                    id = "f.TR-01",
                    type = LevelType.Transition,
                    modifiers = emptySet(),
                    transitionPlan = TransitionPlan(
                        repeatCount = 4,
                        phases = listOf(
                            TransitionPhase(beatCount = 8, pattern = pattern("RLRL")),
                            TransitionPhase(beatCount = 8, pattern = pattern("RRLL")),
                        ),
                    ),
                    technique = technique(),
                    execution = Execution(beatCount = 64),
                    rankTargets = rankTargets(),
                ),
            ),
            nodes = listOf(node("f.TR-01")),
        ).levels.single()

        assertEquals("RLRL", level.pattern.joinToString("") { it.label })
        assertTrue(level.supportsBeatGrid)
        assertFalse(level.playable)
    }

    @Test
    fun `a timed lesson reports seconds instead of beats and waits for the engine`() {
        val level = catalog(
            lessons = listOf(
                lesson("f.EN-01").copy(
                    modifiers = setOf(LevelModifier.Endurance),
                    execution = Execution(
                        durationSeconds = 180,
                        completionMode = CompletionMode.CompletePatternCycle,
                    ),
                ),
            ),
            nodes = listOf(node("f.EN-01")),
        ).levels.single()

        assertEquals(0, level.beatCount)
        assertEquals(180, level.durationSeconds)
        assertFalse(level.playable)
    }

    @Test
    fun `a planned target is played at its entry tempo and density`() {
        val level = catalog(
            lessons = listOf(
                lesson("f.TM-01").copy(
                    type = LevelType.TempoRamp,
                    rankTargets = listOf(
                        RankTarget(
                            rank = PracticeRank.Practice,
                            bpm = 80,
                            hitsPerBeat = 1,
                            tempoRampPlan = TempoRampPlan(
                                mode = "step",
                                direction = "up",
                                phases = listOf(
                                    TempoRampPhase(bpm = 80, beatCount = 32),
                                    TempoRampPhase(bpm = 100, beatCount = 32),
                                ),
                            ),
                        ),
                        RankTarget(rank = PracticeRank.Groove, bpm = 90, hitsPerBeat = 2),
                        RankTarget(rank = PracticeRank.Stage, bpm = 90, hitsPerBeat = 4),
                    ),
                ),
            ),
            nodes = listOf(node("f.TM-01")),
        ).levels.single()

        val practice = level.target(PracticeRank.Practice)
        assertEquals(80, practice.bpm)
        assertTrue(practice.approximated)
        assertFalse(level.target(PracticeRank.Stage).approximated)
        assertNull(level.target(PracticeRank.Groove).tempoRampPlan)
    }

    @Test
    fun `a tempo ramp repeats its pass within one attempt`() {
        val level = catalog(
            lessons = listOf(
                lesson("f.RM-01").copy(
                    type = LevelType.TempoRamp,
                    rankTargets = listOf(
                        RankTarget(
                            rank = PracticeRank.Practice,
                            bpm = 80,
                            hitsPerBeat = 1,
                            tempoRampPlan = TempoRampPlan(
                                mode = "step",
                                direction = "up",
                                phases = listOf(TempoRampPhase(bpm = 80, beatCount = 32)),
                                repeatCount = 3,
                            ),
                        ),
                        RankTarget(rank = PracticeRank.Groove, bpm = 90, hitsPerBeat = 2),
                        RankTarget(rank = PracticeRank.Stage, bpm = 90, hitsPerBeat = 4),
                    ),
                ),
            ),
            nodes = listOf(node("f.RM-01")),
        ).levels.single()

        assertEquals(3, level.target(PracticeRank.Practice).attemptRepeats)
        assertEquals(1, level.target(PracticeRank.Stage).attemptRepeats)
    }

    @Test
    fun `an annotation without a reason is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01").copy(intentionalRollback = " ")),
                nodes = listOf(node("f.ST-01")),
            )
        }
    }

    @Test
    fun `practice grid keeps the hand order of the pattern`() {
        val level = catalog(
            lessons = listOf(lesson("f.ST-01", hands = "RLRRLRLL")),
            nodes = listOf(node("f.ST-01")),
        ).levels.single()

        val row = level.toPracticeGrid().rows.single()

        assertEquals("RLRRLRLL", row.beats.joinToString("") { it.hand.label })
        assertEquals(Hand.Left, row.beats.last().hand)
    }

    @Test
    fun `unison is one unsupported BeatGrid position with both hands`() {
        val unison = catalog(
            lessons = listOf(
                lesson(
                    id = "f.ST-01",
                    type = LevelType.Unison,
                    steps = listOf(PatternStep(setOf(PatternHand.Right, PatternHand.Left))),
                ),
            ),
            nodes = listOf(node("f.ST-01")),
        ).levels.single()

        assertFalse(unison.supportsBeatGrid)
        assertFalse(unison.playable)
        assertEquals("RL", unison.pattern.single().label)
        assertThrows(IllegalArgumentException::class.java) { unison.toPracticeGrid() }
    }

    @Test
    fun `a rest keeps its slot in the pattern and is not played`() {
        val level = catalog(
            lessons = listOf(
                lesson(
                    id = "f.UN-01",
                    type = LevelType.Unison,
                    steps = listOf(
                        PatternStep(setOf(PatternHand.Right, PatternHand.Left)),
                        PatternStep(setOf(PatternHand.Right, PatternHand.Left)),
                        PatternStep(emptySet()),
                        PatternStep(emptySet()),
                    ),
                ),
            ),
            nodes = listOf(node("f.UN-01")),
        ).levels.single()

        assertEquals(4, level.pattern.size)
        assertEquals(listOf(false, false, true, true), level.pattern.map(PatternStep::rest))
        assertEquals("RL", level.pattern.first().label)
        assertEquals(PatternStep.REST_LABEL, level.pattern.last().label)
        assertFalse(level.supportsBeatGrid)
        assertFalse(level.playable)
    }

    @Test
    fun `catalog rejects a pattern made of rests only`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog(
                lessons = listOf(lesson("f.ST-01", steps = listOf(PatternStep(emptySet())))),
                nodes = listOf(node("f.ST-01")),
            )
        }
    }

    private fun catalog(lessons: List<Lesson>, nodes: List<MapNode>): LevelCatalog = LevelCatalog.build(
        schemaVersion = LevelCatalog.CURRENT_SCHEMA_VERSION,
        family = Family(id = "f", name = "Family", description = "Description."),
        lessons = lessons,
        nodes = nodes,
    )

    private fun progress(rank: PracticeRank, vararg completedIds: String): LearningProgress =
        LearningProgress(
            levels = completedIds.associateWith {
                LevelProgress(ranks = mapOf(rank to RankProgress(completed = true, stars = 3)))
            },
        )

    private fun technique() = Technique(strokeStyle = "full_rebound", dynamics = "even", accents = "none")

    private fun pattern(hands: String) = Pattern(
        mode = PatternMode.Repeat,
        steps = hands.map { PatternStep(hands = setOf(PatternHand.fromStorageName(it.toString()))) },
    )

    private fun rankTargets() = PracticeRank.entries.mapIndexed { index, rank ->
        RankTarget(rank = rank, bpm = 90, hitsPerBeat = 1 shl index)
    }

    private fun lesson(
        id: String,
        type: LevelType = LevelType.Steady,
        modifiers: Set<LevelModifier> = emptySet(),
        hands: String = "RL",
        steps: List<PatternStep> = hands.map { label ->
            PatternStep(hands = setOf(PatternHand.fromStorageName(label.toString())))
        },
    ): Lesson = Lesson(
        id = id,
        type = type,
        modifiers = modifiers,
        pattern = Pattern(mode = PatternMode.Repeat, steps = steps),
        technique = technique(),
        execution = Execution(beatCount = 64),
        rankTargets = rankTargets(),
    )

    private fun node(
        lessonId: String,
        column: LevelColumn = LevelColumn.Center,
        prerequisites: Set<String> = emptySet(),
    ): MapNode = MapNode(lessonId = lessonId, column = column, prerequisites = prerequisites)
}
