package com.rudimentor.app.data.levels

/**
 * Runtime model of a generated family package
 * (`learning/course/data/generated/families/<family-id>.json`, schema 9).
 *
 * The course data is the source of truth: the app never adds fields the package
 * does not define. Everything the UI needs beyond the package — the position of a
 * node on the map, the lead hand of a lesson, level titles — is derived here from the data.
 */

enum class LevelType(val storageName: String) {
    Steady("steady"),
    Isolation("isolation"),
    Unison("unison"),
    Transition("transition"),
    SubdivisionSwitch("subdivision_switch"),
    TempoRamp("tempo_ramp"),
    Dynamics("dynamics"),
    ;

    companion object {
        fun fromStorageName(value: String): LevelType = entries.single { it.storageName == value }
    }
}

enum class LevelModifier(val storageName: String) {
    Weak("weak"),
    Endurance("endurance"),
    ;

    companion object {
        fun fromStorageName(value: String): LevelModifier = entries.single { it.storageName == value }
    }
}

enum class LevelColumn(val storageName: String) {
    Left("left"),
    Center("center"),
    Right("right"),
    ;

    val required: Boolean
        get() = this == Center

    companion object {
        fun fromStorageName(value: String): LevelColumn = entries.single { it.storageName == value }
    }
}

enum class PatternMode(val storageName: String) {
    Repeat("repeat"),
    ;

    companion object {
        fun fromStorageName(value: String): PatternMode = entries.single { it.storageName == value }
    }
}

enum class LeadHand(val storageName: String) {
    Right("right"),
    Left("left"),
    ;

    companion object {
        fun fromStorageName(value: String): LeadHand = entries.single { it.storageName == value }
    }
}

enum class PatternHand(val storageName: String) {
    Right("R"),
    Left("L"),
    ;

    companion object {
        fun fromStorageName(value: String): PatternHand = entries.single { it.storageName == value }
    }
}

/** The three approved passes of a level. The package defines a target for each one. */
enum class PracticeRank(val storageName: String) {
    Practice("practice"),
    Groove("groove"),
    Stage("stage"),
    ;

    companion object {
        fun fromStorageName(value: String): PracticeRank = entries.single { it.storageName == value }
    }
}

/** How a level ends when it is measured in time instead of beats. */
enum class CompletionMode(val storageName: String) {
    CompletePatternCycle("complete_pattern_cycle"),
    ;

    companion object {
        fun fromStorageName(value: String): CompletionMode = entries.single { it.storageName == value }
    }
}

/** How a weak-hand lesson treats the two hands. */
enum class WeakStrategy(val storageName: String) {
    WeakLead("weak_lead"),
    WeakOnly("weak_only"),
    ;

    companion object {
        fun fromStorageName(value: String): WeakStrategy = entries.single { it.storageName == value }
    }
}

data class PatternStep(
    val hands: Set<PatternHand>,
) {
    /** A step with no hands is a rest: the beat passes without a stroke. */
    val rest: Boolean = hands.isEmpty()

    val label: String = if (rest) {
        REST_LABEL
    } else {
        PatternHand.entries.filter(hands::contains).joinToString("") { it.storageName }
    }

    companion object {
        /** How a rest reads in a pattern line, e.g. `RL––`. */
        const val REST_LABEL = "–"
    }
}

data class Pattern(
    val mode: PatternMode,
    val steps: List<PatternStep>,
)

/**
 * Required execution of a lesson. `strokeStyle`, `dynamics` and `accents` stay free-form
 * strings in the package, so they are carried through as data instead of app-side enums.
 */
data class Technique(
    val strokeStyle: String,
    val dynamics: String,
    val accents: String,
)

/**
 * How long one official attempt lasts. A lesson is measured either in beats
 * ([beatCount]) or in time ([durationSeconds] with a [completionMode]) — never in both.
 */
data class Execution(
    val beatCount: Int? = null,
    val durationSeconds: Int? = null,
    val completionMode: CompletionMode? = null,
) {
    val timed: Boolean get() = durationSeconds != null
}

data class SubdivisionPlan(
    val blockBeats: Int,
    val hitsPerBeat: List<Int>,
)

data class TempoRampPhase(
    val bpm: Int,
    val beatCount: Int,
)

/**
 * A tempo ramp. One pass through [phases] is usually shorter than a meaningful attempt, so
 * the course pipeline computes [repeatCount]: how many passes one official attempt plays.
 */
data class TempoRampPlan(
    val mode: String,
    val direction: String,
    val phases: List<TempoRampPhase>,
    val repeatCount: Int = 1,
) {
    /** Beats in one pass through the phases. */
    val passBeats: Int get() = phases.sumOf { it.beatCount }
}

/**
 * One rank of a lesson. Schema 7 lets a target replace the fixed [bpm] with a tempo ramp
 * and the fixed [hitsPerBeat] with a subdivision plan, so both scalars are the *entry*
 * values: the first phase of the ramp and the first block of the subdivision plan.
 *
 * Both plans are played: [hitsPerBeatAtBeat] gives the density of any beat of the attempt
 * and [bpmAtBeat] gives its tempo, so the scalars are only what a level without a plan
 * answers for every beat.
 */
data class RankTarget(
    val rank: PracticeRank,
    val bpm: Int,
    val hitsPerBeat: Int,
    val subdivisionPlan: SubdivisionPlan? = null,
    val tempoRampPlan: TempoRampPlan? = null,
    /** Why this target is allowed to break the density growth rule, when it is. */
    val densityException: String? = null,
) {
    /**
     * Tempo of [beat], counted from the first beat of the attempt.
     *
     * One pass through the phases of a ramp is shorter than an attempt, so the passes
     * repeat (decision 100) and the beat is read within its pass. A target without a ramp
     * answers its own [bpm] for every beat.
     */
    fun bpmAtBeat(beat: Int): Int {
        val plan = tempoRampPlan ?: return bpm
        val passBeats = plan.passBeats
        if (passBeats <= 0) return bpm
        var remaining = beat.mod(passBeats)
        plan.phases.forEach { phase ->
            if (remaining < phase.beatCount) return phase.bpm
            remaining -= phase.beatCount
        }
        return plan.phases.last().bpm
    }

    /**
     * Density of [beat], counted from the first beat of the attempt.
     *
     * A subdivision plan holds one density per block of [SubdivisionPlan.blockBeats] beats
     * and the blocks cycle: `1 → 2 → 1` over blocks of 8 beats fills 48 beats as two passes
     * of the same three blocks (decision 98). A target without a plan answers its own
     * density for every beat, so callers never branch on the plan themselves.
     */
    fun hitsPerBeatAtBeat(beat: Int): Int {
        val plan = subdivisionPlan ?: return hitsPerBeat
        val block = beat / plan.blockBeats
        return plan.hitsPerBeat[block % plan.hitsPerBeat.size]
    }

    /** How many times the authored execution repeats within one attempt at this rank. */
    val attemptRepeats: Int get() = tempoRampPlan?.repeatCount ?: 1
}

data class TransitionPhase(
    val beatCount: Int,
    val pattern: Pattern,
)

/** A lesson that cycles through several patterns instead of repeating one. */
data class TransitionPlan(
    val repeatCount: Int,
    val phases: List<TransitionPhase>,
)

/**
 * One block of an attempt: [beatCount] beats played on [steps].
 *
 * A one-pattern lesson is a single block. A transition lesson is a chain of them, and the
 * whole chain repeats [TransitionPlan.repeatCount] times inside one attempt, so the practice
 * engine walks the same loop for both kinds of level (decision 141).
 */
data class PracticePhase(
    val index: Int,
    val beatCount: Int,
    val steps: List<PatternStep>,
)

data class WeakFocus(
    val strategy: WeakStrategy,
    val authoredWeakHand: PatternHand,
    val adaptToUser: Boolean,
)

data class Family(
    val id: String,
    val name: String,
    val description: String,
)

/** One lesson exactly as the package defines it. */
data class Lesson(
    val id: String,
    val type: LevelType,
    val modifiers: Set<LevelModifier>,
    val pattern: Pattern? = null,
    val transitionPlan: TransitionPlan? = null,
    val weakFocus: WeakFocus? = null,
    val technique: Technique,
    val execution: Execution,
    val rankTargets: List<RankTarget>,
    /** True when the lesson switches subdivision inside an unfinished sticking cycle. */
    val midCycleSwitch: Boolean = false,
    /** Why this lesson deliberately steps back from the tempo reached before it. */
    val intentionalRollback: String? = null,
)

/** One map node exactly as the package defines it. */
data class MapNode(
    val lessonId: String,
    val column: LevelColumn,
    val prerequisites: Set<String>,
)

/**
 * A lesson merged with its map node and its place on the map.
 *
 * The package stores no coordinates: the map is a graph. [row] follows the chain of
 * required center levels, and the map is exactly three columns wide — the required path plus
 * one optional column per side. An optional level sits on the row of the level it branches
 * from; when that cell is taken it steps one row forward along its own column, so a branch
 * never widens the map (decision 121).
 */
data class Level(
    val lesson: Lesson,
    val node: MapNode,
    val row: Int,
) {
    val id: String get() = lesson.id
    val column: LevelColumn get() = node.column
    val prerequisiteIds: Set<String> get() = node.prerequisites
    val type: LevelType get() = lesson.type
    val modifiers: Set<LevelModifier> get() = lesson.modifiers
    val technique: Technique get() = lesson.technique
    val rankTargets: List<RankTarget> get() = lesson.rankTargets
    val beatCount: Int get() = lesson.execution.beatCount ?: 0
    val durationSeconds: Int? get() = lesson.execution.durationSeconds

    /** The pattern to show and to play. A transition lesson previews its first phase. */
    val pattern: List<PatternStep> = lesson.pattern?.steps
        ?: lesson.transitionPlan?.phases?.firstOrNull()?.pattern?.steps
        ?: emptyList()

    val patternMode: PatternMode = lesson.pattern?.mode
        ?: lesson.transitionPlan?.phases?.firstOrNull()?.pattern?.mode
        ?: PatternMode.Repeat

    /** True when the lesson cycles through several patterns inside one attempt. */
    val phased: Boolean = lesson.transitionPlan != null

    /** The blocks one pass of an attempt plays, in order. */
    val phases: List<PracticePhase> = lesson.transitionPlan?.phases
        ?.mapIndexed { index, phase ->
            PracticePhase(index = index, beatCount = phase.beatCount, steps = phase.pattern.steps)
        }
        ?: listOf(PracticePhase(index = 0, beatCount = lesson.execution.beatCount ?: 0, steps = pattern))

    /** How many times the chain of [phases] repeats inside one attempt. */
    val phaseRepeats: Int = lesson.transitionPlan?.repeatCount ?: 1

    /** `singles.ST-07` is shown as `07` on the map. */
    val displayNumber: String = lesson.id.substringAfterLast('-')

    /**
     * The level code: track plus number, `singles.ST-07` -> `ST-07`. Numbers alone repeat
     * across tracks (`ST-01`, `RM-01`, `DI-01`), so the map labels nodes with the code.
     */
    val displayCode: String = lesson.id.substringAfterLast('.')

    /** The package has no lead hand; the first step of the pattern decides the pad shape. */
    val leadHand: LeadHand = when (pattern.firstOrNull()?.hands?.singleOrNull()) {
        PatternHand.Left -> LeadHand.Left
        else -> LeadHand.Right
    }

    /**
     * A beat row holds one hand per beat, so a unison step and a rest both fall outside
     * it. Only the metronome-style [toPracticeGrid] conversion needs this; the practice
     * track itself plays unison and rests (see [playable]).
     */
    val supportsBeatGrid: Boolean = phases.isNotEmpty() &&
        phases.all { phase -> phase.steps.isNotEmpty() && phase.steps.all { it.hands.size == 1 } }

    /**
     * Whether the practice engine can run this level today. Phase levels are played since
     * decision 141 and timed lessons since decision 154, which turns a duration into beats.
     * A unison step is one note struck by both hands and a rest is a position without a
     * note, so both fit the track (decision 212); a level only needs a stroke to judge —
     * every block has to carry at least one — and a length in beats or minutes.
     */
    val playable: Boolean = phases.isNotEmpty() &&
        phases.all { phase -> phase.steps.any { !it.rest } } &&
        (beatCount > 0 || (durationSeconds ?: 0) > 0)

    fun target(rank: PracticeRank): RankTarget = rankTargets.single { it.rank == rank }

    /**
     * Tempo of every beat of one attempt at [target], in order: what the metronome has to
     * play for the click to stay on the notes of a tempo ramp. A level without a ramp
     * yields one tempo repeated, which the audio engine treats as a plain fixed tempo.
     */
    fun tempoPlan(target: RankTarget, bpm: Int = target.bpm): IntArray =
        IntArray(beatsPerAttempt(target, bpm)) { beat -> target.bpmAtBeat(beat) }

    /**
     * Beats of one full sticking cycle at [hitsPerBeat]: a pattern can span a fraction of a
     * beat (`RL` at four hits per beat) or several beats (`RL` at one hit per beat), so the
     * cycle is the least common multiple of the two densities, read back as beats.
     */
    fun cycleBeats(hitsPerBeat: Int): Int {
        val steps = pattern.size
        if (steps <= 0 || hitsPerBeat <= 0) return 1
        var a = steps
        var b = hitsPerBeat
        while (b != 0) {
            val rest = a % b
            a = b
            b = rest
        }
        return steps / a
    }

    /**
     * Beats a timed attempt plays at [bpm]: the stated duration is a floor, and the attempt
     * runs on to the end of the sticking cycle it lands in (`complete_pattern_cycle`,
     * decision 105). The length therefore depends on the tempo the learner picked, which is
     * why every beat-count entry point below takes a tempo.
     */
    fun timedBeats(target: RankTarget, bpm: Int = target.bpm): Int {
        val seconds = durationSeconds ?: return 0
        if (seconds <= 0 || bpm <= 0 || pattern.isEmpty()) return 0
        val cycle = cycleBeats(target.hitsPerBeat)
        val floorBeats = kotlin.math.ceil(seconds * bpm / 60.0).toInt()
        val cycles = kotlin.math.ceil(floorBeats / cycle.toDouble()).toInt()
        return cycle * maxOf(1, cycles)
    }

    /**
     * The blocks one pass of an attempt at [target] plays. A timed lesson has no authored
     * beat count, so its single block is sized from the duration at [bpm]; every other
     * lesson plays the blocks the package authored ([phases]).
     */
    fun attemptPhases(target: RankTarget, bpm: Int = target.bpm): List<PracticePhase> =
        if (durationSeconds != null) {
            listOf(PracticePhase(index = 0, beatCount = timedBeats(target, bpm), steps = pattern))
        } else {
            phases
        }

    /** Beats one official attempt plays: the block chain, every pass of it. */
    fun beatsPerAttempt(target: RankTarget, bpm: Int = target.bpm): Int =
        phaseRepeats * target.attemptRepeats *
            attemptPhases(target, bpm).sumOf { if (it.steps.isEmpty()) 0 else it.beatCount }

    /**
     * How many notes one official attempt at [target] plays: every beat of every block of
     * every pass, at the density that beat is played on. Shown by the debug dump and used
     * by the tests, so both read the same number the practice engine builds.
     */
    fun noteCount(target: RankTarget, bpm: Int = target.bpm): Int {
        var beat = 0
        var notes = 0
        repeat(phaseRepeats * target.attemptRepeats) {
            attemptPhases(target, bpm).forEach { phase ->
                if (phase.steps.isEmpty()) return@forEach
                repeat(phase.beatCount) {
                    notes += target.hitsPerBeatAtBeat(beat)
                    beat += 1
                }
            }
        }
        return notes
    }
}

enum class LevelNodeState {
    Completed,
    Current,
    Available,
    Locked,
}

data class LevelCatalog(
    val schemaVersion: Int,
    val mapVersion: Int,
    val family: Family,
    val levels: List<Level>,
) {
    private val levelsById = levels.associateBy(Level::id)

    val lastRow: Int = levels.maxOfOrNull(Level::row) ?: 0

    /** Whether the map has optional columns at all. Drives the width of the drawn map. */
    val hasSideLevels: Boolean = levels.any { it.column != LevelColumn.Center }

    /** The chain of required levels, bottom to top. */
    val centerPath: List<Level> = levels.filter { it.column == LevelColumn.Center }.sortedBy(Level::row)

    fun level(id: String): Level? = levelsById[id]

    companion object {
        const val CURRENT_SCHEMA_VERSION = 9
        const val MIN_BPM = 40
        const val MAX_BPM = 240

        /**
         * Validates the package the same way `tools/validate_family.py` does, then derives
         * the map layout. Mirrors the semantic validator so bad data fails at load, not in the UI.
         */
        fun build(
            schemaVersion: Int,
            mapVersion: Int,
            family: Family,
            lessons: List<Lesson>,
            nodes: List<MapNode>,
        ): LevelCatalog {
            require(schemaVersion == CURRENT_SCHEMA_VERSION) {
                "Unsupported family package schema: $schemaVersion"
            }
            require(mapVersion > 0) { "The map version must be positive" }
            require(family.id.isNotBlank()) { "The family must have an id" }
            require(lessons.isNotEmpty()) { "The family package must contain at least one lesson" }
            require(lessons.map(Lesson::id).distinct().size == lessons.size) { "Lesson IDs must be unique" }
            require(nodes.map(MapNode::lessonId).distinct().size == nodes.size) { "Map node IDs must be unique" }

            val lessonsById = lessons.associateBy(Lesson::id)
            require(nodes.map(MapNode::lessonId).toSet() == lessonsById.keys) {
                "Every lesson must have exactly one map node"
            }
            lessons.forEach { validateLesson(family, it) }
            nodes.forEach { node ->
                require(node.lessonId !in node.prerequisites) { "${node.lessonId}: a lesson cannot require itself" }
                require(node.prerequisites.all(lessonsById::containsKey)) {
                    "${node.lessonId}: prerequisites must point at lessons of this family"
                }
                require(node.column == LevelColumn.Center || node.prerequisites.size == 1) {
                    "${node.lessonId}: an optional lesson has exactly one entry"
                }
            }

            val rows = deriveLayout(nodes)
            val levels = nodes.map { node ->
                Level(
                    lesson = lessonsById.getValue(node.lessonId),
                    node = node,
                    row = rows.getValue(node.lessonId),
                )
            }
            return LevelCatalog(
                schemaVersion = schemaVersion,
                mapVersion = mapVersion,
                family = family,
                levels = levels,
            )
        }

        private fun validateLesson(family: Family, lesson: Lesson) {
            require(lesson.id.startsWith("${family.id}.")) {
                "${lesson.id}: lesson IDs must use the '${family.id}.' namespace"
            }
            val steps = lesson.pattern?.steps
            val phases = lesson.transitionPlan?.phases
            require((steps == null) != (phases == null)) {
                "${lesson.id}: a lesson defines either a pattern or a transition plan"
            }
            require((lesson.type == LevelType.Transition) == (phases != null)) {
                "${lesson.id}: a transition plan belongs to a transition lesson"
            }
            val allSteps = steps ?: phases.orEmpty().flatMap { it.pattern.steps }
            require(allSteps.isNotEmpty()) { "${lesson.id}: pattern must not be empty" }
            // A step without hands is a rest, exactly as `tools/validate_family.py` reads it.
            // Only a pattern made of nothing but rests is data no lesson can be built from.
            val struck = allSteps.filterNot(PatternStep::rest)
            require(struck.isNotEmpty()) {
                "${lesson.id}: a pattern needs at least one step with a hand"
            }
            require(
                lesson.type != LevelType.Unison ||
                    struck.all { it.hands == PatternHand.entries.toSet() },
            ) {
                "${lesson.id}: every unison step must contain right and left hands"
            }
            require(lesson.type == LevelType.Unison || struck.all { it.hands.size == 1 }) {
                "${lesson.id}: multi-hand steps are reserved for unison levels"
            }
            phases?.forEach { phase ->
                require(phase.beatCount > 0) { "${lesson.id}: every transition phase needs beats" }
            }
            require(lesson.transitionPlan == null || lesson.transitionPlan.repeatCount > 0) {
                "${lesson.id}: a transition plan must repeat at least once"
            }
            require(lesson.intentionalRollback?.isNotBlank() != false) {
                "${lesson.id}: an intentional rollback must carry a reason"
            }
            validateExecution(lesson)
            validateRankTargets(lesson)
        }

        private fun validateExecution(lesson: Lesson) {
            val execution = lesson.execution
            require((execution.beatCount == null) != (execution.durationSeconds == null)) {
                "${lesson.id}: execution is measured either in beats or in seconds"
            }
            execution.beatCount?.let {
                require(it > 0) { "${lesson.id}: beatCount must be positive" }
                require(execution.completionMode == null) {
                    "${lesson.id}: completionMode belongs to a timed lesson"
                }
            }
            execution.durationSeconds?.let {
                require(it > 0) { "${lesson.id}: durationSeconds must be positive" }
                require(execution.completionMode != null) {
                    "${lesson.id}: a timed lesson must say how it completes"
                }
            }
        }

        private fun validateRankTargets(lesson: Lesson) {
            require(lesson.rankTargets.size == PracticeRank.entries.size) {
                "${lesson.id}: rank targets must not contain duplicates"
            }
            require(lesson.rankTargets.map(RankTarget::rank).toSet() == PracticeRank.entries.toSet()) {
                "${lesson.id}: rank targets must define Practice, Groove and Stage exactly once"
            }
            lesson.rankTargets.forEach { target ->
                require(target.bpm in MIN_BPM..MAX_BPM) {
                    "${lesson.id}: rank target BPM must be between $MIN_BPM and $MAX_BPM"
                }
                require(target.hitsPerBeat > 0) { "${lesson.id}: hitsPerBeat must be positive" }
                target.subdivisionPlan?.let { plan ->
                    require(plan.blockBeats > 0) { "${lesson.id}: subdivision blocks need beats" }
                    require(plan.hitsPerBeat.isNotEmpty() && plan.hitsPerBeat.all { it > 0 }) {
                        "${lesson.id}: a subdivision plan needs positive densities"
                    }
                }
                target.tempoRampPlan?.let { plan ->
                    require(plan.phases.isNotEmpty()) { "${lesson.id}: a tempo ramp needs phases" }
                    require(plan.phases.all { it.bpm in MIN_BPM..MAX_BPM && it.beatCount > 0 }) {
                        "${lesson.id}: every tempo ramp phase needs a valid tempo and beats"
                    }
                    require(plan.repeatCount > 0) {
                        "${lesson.id}: a tempo ramp must repeat at least once per attempt"
                    }
                }
                require(target.densityException?.isNotBlank() != false) {
                    "${lesson.id}: a density exception must carry a reason"
                }
            }
        }

        /** How far an optional level may step away from its anchor before the map gives up. */
        private const val MAX_ROW_SHIFT = 4

        /**
         * Rows follow the required path: the center levels form one chain, and its index in
         * that chain is the row. An optional level enters through its single prerequisite and
         * lands in its own column — on the anchor's row when that comes from the required
         * path, one row further when it continues an optional chain. If the cell is taken the
         * level steps forward along the same column (then backward, when forward is full), so
         * the map stays three columns wide (decision 121).
         */
        private fun deriveLayout(nodes: List<MapNode>): Map<String, Int> {
            val byId = nodes.associateBy(MapNode::lessonId)
            val depths = deriveDepths(byId)

            val centers = nodes.filter { it.column == LevelColumn.Center }
                .sortedBy { depths.getValue(it.lessonId) }
            require(centers.isNotEmpty()) { "The map must contain a required path" }
            centers.zipWithNext { lower, upper ->
                require(depths.getValue(lower.lessonId) < depths.getValue(upper.lessonId)) {
                    "${upper.lessonId}: the required path must be a single chain"
                }
            }
            val rows = centers.withIndex().associate { (index, node) -> node.lessonId to index }.toMutableMap()
            val taken = centers.indices.map { it to LevelColumn.Center }.toMutableSet()

            nodes.filter { it.column != LevelColumn.Center }
                .sortedWith(compareBy({ depths.getValue(it.lessonId) }, MapNode::lessonId))
                .forEach { node ->
                    val anchorId = node.prerequisites.single()
                    val anchor = byId.getValue(anchorId)
                    require(anchor.column == LevelColumn.Center || anchor.column == node.column) {
                        "${node.lessonId}: an optional chain must stay in its own column"
                    }
                    val base = rows.getValue(anchorId) +
                        if (anchor.column == LevelColumn.Center) 0 else 1
                    val row = requireNotNull(freeRow(base, node.column, taken)) {
                        "${node.lessonId}: the optional column has no free cell near row $base"
                    }
                    rows[node.lessonId] = row
                    taken += row to node.column
                }

            return rows
        }

        /**
         * The cell an optional level takes: its anchor's row when free, otherwise the nearest
         * row along the same column — forward first — whose whole vertical path is free, so the
         * connector to it never runs through another level.
         */
        private fun freeRow(
            base: Int,
            column: LevelColumn,
            taken: Set<Pair<Int, LevelColumn>>,
        ): Int? {
            val candidates = sequenceOf(base) +
                (1..MAX_ROW_SHIFT).asSequence().flatMap { sequenceOf(base + it, base - it) }
            return candidates.firstOrNull { candidate ->
                candidate >= 0 &&
                    (minOf(base, candidate)..maxOf(base, candidate)).none { row ->
                        (row != base || candidate == base) && (row to column) in taken
                    }
            }
        }

        /** Longest prerequisite chain that leads to a node. Rejects cycles, like the validator. */
        private fun deriveDepths(byId: Map<String, MapNode>): Map<String, Int> {
            val depths = mutableMapOf<String, Int>()
            val visiting = mutableSetOf<String>()

            fun depthOf(id: String): Int {
                depths[id]?.let { return it }
                require(visiting.add(id)) { "$id: prerequisites must not form a cycle" }
                val node = byId.getValue(id)
                val depth = (node.prerequisites.maxOfOrNull { depthOf(it) + 1 }) ?: 0
                visiting.remove(id)
                depths[id] = depth
                return depth
            }

            byId.keys.forEach { depthOf(it) }
            return depths
        }
    }
}
