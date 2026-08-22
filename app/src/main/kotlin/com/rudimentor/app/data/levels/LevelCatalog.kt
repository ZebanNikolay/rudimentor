package com.rudimentor.app.data.levels

/**
 * Runtime model of a generated family package
 * (`learning/course/data/generated/families/<family-id>.json`, schema 7).
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
    val label: String = PatternHand.entries.filter(hands::contains).joinToString("") { it.storageName }
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

data class TempoRampPlan(
    val mode: String,
    val direction: String,
    val phases: List<TempoRampPhase>,
)

/**
 * One rank of a lesson. Schema 7 lets a target replace the fixed [bpm] with a tempo ramp
 * and the fixed [hitsPerBeat] with a subdivision plan, so both scalars are the *entry*
 * values: the first phase of the ramp and the first block of the subdivision plan. The
 * practice engine plays a planned level at those entry values until it can follow a plan.
 */
data class RankTarget(
    val rank: PracticeRank,
    val bpm: Int,
    val hitsPerBeat: Int,
    val subdivisionPlan: SubdivisionPlan? = null,
    val tempoRampPlan: TempoRampPlan? = null,
) {
    /** True when the engine plays an approximation of the authored plan. */
    val approximated: Boolean get() = subdivisionPlan != null || tempoRampPlan != null
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
 * required center levels, and a side level sits on the row of the level it branches from.
 * Several side levels can branch from the same row on the same side, so [lateral] is the
 * slot away from the center chain: 0 for a center level, 1 for the first side level, and so on.
 */
data class Level(
    val lesson: Lesson,
    val node: MapNode,
    val row: Int,
    val lateral: Int,
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

    /** `singles.ST-07` is shown as `07` on the map. */
    val displayNumber: String = lesson.id.substringAfterLast('-')

    /** The package has no lead hand; the first step of the pattern decides the pad shape. */
    val leadHand: LeadHand = when (pattern.firstOrNull()?.hands?.singleOrNull()) {
        PatternHand.Left -> LeadHand.Left
        else -> LeadHand.Right
    }

    val supportsBeatGrid: Boolean = pattern.isNotEmpty() && pattern.all { it.hands.size == 1 }

    /**
     * Whether the practice engine can run this level today. Transition lessons need phase
     * switching, timed lessons need a duration-driven attempt, and unison lessons need two
     * hands on one position — until then those levels are previewed, not played.
     */
    val playable: Boolean = supportsBeatGrid &&
        lesson.transitionPlan == null &&
        !lesson.execution.timed &&
        beatCount > 0

    fun target(rank: PracticeRank): RankTarget = rankTargets.single { it.rank == rank }
}

enum class LevelNodeState {
    Completed,
    Current,
    Available,
    Locked,
}

data class LevelCatalog(
    val schemaVersion: Int,
    val family: Family,
    val levels: List<Level>,
) {
    private val levelsById = levels.associateBy(Level::id)

    val lastRow: Int = levels.maxOfOrNull(Level::row) ?: 0

    /** How far the map spreads sideways, per side. Drives the width of the drawn map. */
    val lastLateral: Int = levels.maxOfOrNull(Level::lateral) ?: 0

    /** The chain of required levels, bottom to top. */
    val centerPath: List<Level> = levels.filter { it.column == LevelColumn.Center }.sortedBy(Level::row)

    fun level(id: String): Level? = levelsById[id]

    companion object {
        const val CURRENT_SCHEMA_VERSION = 7
        const val MIN_BPM = 40
        const val MAX_BPM = 250

        /**
         * Validates the package the same way `tools/validate_family.py` does, then derives
         * the map layout. Mirrors the semantic validator so bad data fails at load, not in the UI.
         */
        fun build(
            schemaVersion: Int,
            family: Family,
            lessons: List<Lesson>,
            nodes: List<MapNode>,
        ): LevelCatalog {
            require(schemaVersion == CURRENT_SCHEMA_VERSION) {
                "Unsupported family package schema: $schemaVersion"
            }
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
                require(node.column == LevelColumn.Center || node.prerequisites.isNotEmpty()) {
                    "${node.lessonId}: a side lesson must branch off the required path"
                }
            }

            val layout = deriveLayout(nodes)
            val levels = nodes.map { node ->
                val place = layout.getValue(node.lessonId)
                Level(
                    lesson = lessonsById.getValue(node.lessonId),
                    node = node,
                    row = place.row,
                    lateral = place.lateral,
                )
            }
            return LevelCatalog(schemaVersion = schemaVersion, family = family, levels = levels)
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
            require(allSteps.all { it.hands.isNotEmpty() }) {
                "${lesson.id}: every pattern step must contain at least one hand"
            }
            require(
                lesson.type != LevelType.Unison ||
                    allSteps.all { it.hands == PatternHand.entries.toSet() },
            ) {
                "${lesson.id}: every unison step must contain right and left hands"
            }
            require(lesson.type == LevelType.Unison || allSteps.all { it.hands.size == 1 }) {
                "${lesson.id}: multi-hand steps are reserved for unison levels"
            }
            phases?.forEach { phase ->
                require(phase.beatCount > 0) { "${lesson.id}: every transition phase needs beats" }
            }
            require(lesson.transitionPlan == null || lesson.transitionPlan.repeatCount > 0) {
                "${lesson.id}: a transition plan must repeat at least once"
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
                }
            }
        }

        private data class Place(val row: Int, val lateral: Int)

        /**
         * Rows follow the required path: the center levels form one chain, and its index in
         * that chain is the row. A side level shares the row of the level it branches from,
         * which keeps a branch next to the level that unlocked it — several side levels of
         * the same row and side then take lateral slots 1, 2, 3 in graph order.
         */
        private fun deriveLayout(nodes: List<MapNode>): Map<String, Place> {
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

            // A side level takes the row of its deepest prerequisite, following chains of
            // side levels back to the center chain they hang from.
            fun rowOf(id: String): Int {
                rows[id]?.let { return it }
                val node = byId.getValue(id)
                val row = node.prerequisites.maxOf { rowOf(it) }
                rows[id] = row
                return row
            }
            nodes.forEach { rowOf(it.lessonId) }

            val sides = nodes.filter { it.column != LevelColumn.Center }
            val laterals = sides
                .groupBy { rows.getValue(it.lessonId) to it.column }
                .flatMap { (_, group) ->
                    group.sortedWith(compareBy({ depths.getValue(it.lessonId) }, MapNode::lessonId))
                        .mapIndexed { index, node -> node.lessonId to index + 1 }
                }
                .toMap()

            return nodes.associate { node ->
                node.lessonId to Place(
                    row = rows.getValue(node.lessonId),
                    lateral = laterals[node.lessonId] ?: 0,
                )
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
