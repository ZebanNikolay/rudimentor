package com.rudimentor.app.data.levels

/**
 * Runtime model of a generated family package
 * (`learning/course/data/generated/families/<family-id>.json`).
 *
 * The course data is the source of truth: the app never adds fields the package
 * does not define. Everything the UI needs beyond the package — the map row of a
 * node, the lead hand of a lesson, level titles — is derived here from the data.
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

data class Execution(
    val beatCount: Int,
)

data class RankTarget(
    val rank: PracticeRank,
    val bpm: Int,
    val hitsPerBeat: Int,
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
    val pattern: Pattern,
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
 * A lesson merged with its map node, plus the row derived from the prerequisite graph.
 * The package stores no row: the map is a graph, and the row is its depth.
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
    val pattern: List<PatternStep> get() = lesson.pattern.steps
    val patternMode: PatternMode get() = lesson.pattern.mode
    val technique: Technique get() = lesson.technique
    val beatCount: Int get() = lesson.execution.beatCount
    val rankTargets: List<RankTarget> get() = lesson.rankTargets

    /** `singles.ST-07` is shown as `07` on the map. */
    val displayNumber: String = lesson.id.substringAfterLast('-')

    /** The package has no lead hand; the first step of the pattern decides the pad shape. */
    val leadHand: LeadHand = when (lesson.pattern.steps.firstOrNull()?.hands?.singleOrNull()) {
        PatternHand.Left -> LeadHand.Left
        else -> LeadHand.Right
    }

    val supportsBeatGrid: Boolean = lesson.pattern.steps.all { it.hands.size == 1 }

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

    fun level(id: String): Level? = levelsById[id]

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MIN_BPM = 40
        const val MAX_BPM = 250

        /**
         * Validates the package the same way `tools/validate_family.py` does, then derives
         * the map rows. Mirrors the semantic validator so bad data fails at load, not in the UI.
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
            }

            val rows = deriveRows(nodes)
            val levels = nodes.map { node ->
                Level(
                    lesson = lessonsById.getValue(node.lessonId),
                    node = node,
                    row = rows.getValue(node.lessonId),
                )
            }
            levels.groupBy(Level::row).forEach { (row, rowLevels) ->
                require(rowLevels.count { it.column == LevelColumn.Center } == 1) {
                    "Row $row: every row must have exactly one required center level"
                }
                require(rowLevels.map(Level::column).distinct().size == rowLevels.size) {
                    "Row $row: a column may contain only one level"
                }
            }
            return LevelCatalog(schemaVersion = schemaVersion, family = family, levels = levels)
        }

        private fun validateLesson(family: Family, lesson: Lesson) {
            require(lesson.id.startsWith("${family.id}.")) {
                "${lesson.id}: lesson IDs must use the '${family.id}.' namespace"
            }
            require(lesson.pattern.steps.isNotEmpty()) { "${lesson.id}: pattern must not be empty" }
            require(lesson.pattern.steps.all { it.hands.isNotEmpty() }) {
                "${lesson.id}: every pattern step must contain at least one hand"
            }
            require(
                lesson.type != LevelType.Unison ||
                    lesson.pattern.steps.all { it.hands == PatternHand.entries.toSet() },
            ) {
                "${lesson.id}: every unison step must contain right and left hands"
            }
            require(lesson.type == LevelType.Unison || lesson.pattern.steps.all { it.hands.size == 1 }) {
                "${lesson.id}: multi-hand steps are reserved for unison levels"
            }
            require(lesson.execution.beatCount > 0) { "${lesson.id}: beatCount must be positive" }
            require(lesson.rankTargets.map(RankTarget::rank).toSet() == PracticeRank.entries.toSet()) {
                "${lesson.id}: rank targets must define Practice, Groove and Stage exactly once"
            }
            require(lesson.rankTargets.size == PracticeRank.entries.size) {
                "${lesson.id}: rank targets must not contain duplicates"
            }
            require(lesson.rankTargets.all { it.bpm in MIN_BPM..MAX_BPM }) {
                "${lesson.id}: rank target BPM must be between $MIN_BPM and $MAX_BPM"
            }
            require(lesson.rankTargets.all { it.hitsPerBeat > 0 }) {
                "${lesson.id}: hitsPerBeat must be positive"
            }
        }

        /**
         * Row = longest prerequisite chain that leads to the node. A node without
         * prerequisites sits on row 0. Rejects cycles, like the semantic validator does.
         */
        private fun deriveRows(nodes: List<MapNode>): Map<String, Int> {
            val byId = nodes.associateBy(MapNode::lessonId)
            val rows = mutableMapOf<String, Int>()
            val visiting = mutableSetOf<String>()

            fun rowOf(id: String): Int {
                rows[id]?.let { return it }
                require(visiting.add(id)) { "$id: prerequisites must not form a cycle" }
                val node = byId.getValue(id)
                val row = (node.prerequisites.maxOfOrNull { rowOf(it) + 1 }) ?: 0
                visiting.remove(id)
                rows[id] = row
                return row
            }

            nodes.forEach { rowOf(it.lessonId) }
            return rows
        }
    }
}
