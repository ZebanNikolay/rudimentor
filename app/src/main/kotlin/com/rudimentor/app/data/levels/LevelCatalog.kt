package com.rudimentor.app.data.levels

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

enum class LevelRole(val storageName: String) {
    Lesson("lesson"),
    Checkpoint("checkpoint"),
    ;

    companion object {
        fun fromStorageName(value: String): LevelRole = entries.single { it.storageName == value }
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

enum class PracticeRank(val storageName: String) {
    Practice("practice"),
    Groove("groove"),
    Stage("stage"),
    Rockstar("rockstar"),
    ;

    companion object {
        fun fromStorageName(value: String): PracticeRank = entries.single { it.storageName == value }
    }
}

data class PatternStep(
    val hands: Set<PatternHand>,
    val accent: Boolean,
) {
    val label: String = PatternHand.entries.filter(hands::contains).joinToString("") { it.storageName }
}

data class RankTarget(
    val rank: PracticeRank,
    val bpm: Int,
    val repetitions: Int,
)

data class Level(
    val id: String,
    val row: Int,
    val column: LevelColumn,
    val role: LevelRole,
    val type: LevelType,
    val modifiers: Set<LevelModifier>,
    val title: String,
    val description: String,
    val pattern: List<PatternStep>,
    val leadHand: LeadHand,
    val rankTargets: List<RankTarget>,
    val prerequisiteIds: Set<String>,
) {
    val displayNumber: String = id.substringAfter('-')

    val supportsBeatGrid: Boolean = pattern.all { it.hands.size == 1 }

    fun target(rank: PracticeRank): RankTarget = rankTargets.single { it.rank == rank }
}

data class LevelTier(
    val id: String,
    val name: String,
    val levels: List<Level>,
) {
    private val levelsById = levels.associateBy(Level::id)

    fun level(id: String): Level? = levelsById[id]
}

enum class LevelNodeState {
    Completed,
    Current,
    Available,
    Locked,
}

data class LevelCatalog(
    val schemaVersion: Int,
    val tiers: List<LevelTier>,
) {
    fun level(id: String): Level? = tiers.firstNotNullOfOrNull { it.level(id) }

    fun tierForLevel(id: String): LevelTier? = tiers.firstOrNull { it.level(id) != null }

    fun validated(): LevelCatalog = apply {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported level catalog schema: $schemaVersion"
        }
        require(tiers.isNotEmpty()) { "The level catalog must contain at least one tier" }
        require(tiers.map(LevelTier::id).distinct().size == tiers.size) { "Tier IDs must be unique" }

        val allLevels = tiers.flatMap(LevelTier::levels)
        require(allLevels.map(Level::id).distinct().size == allLevels.size) { "Level IDs must be unique" }
        tiers.forEach(::validateTier)
    }

    private fun validateTier(tier: LevelTier) {
        if (tier.levels.isEmpty()) return

        val ids = tier.levels.mapTo(mutableSetOf(), Level::id)
        val byId = tier.levels.associateBy(Level::id)
        tier.levels.forEach { level ->
            require(level.row >= 0) { "${level.id}: row must not be negative" }
            require(level.prerequisiteIds.all(ids::contains)) {
                "${level.id}: prerequisites must belong to the same tier"
            }
            require(level.prerequisiteIds.all { prerequisiteId ->
                val prerequisite = byId.getValue(prerequisiteId)
                prerequisite.row < level.row ||
                    (
                        prerequisite.row == level.row &&
                            prerequisite.column == LevelColumn.Center &&
                            level.column != LevelColumn.Center
                    )
            }) {
                "${level.id}: prerequisites must appear on an earlier row, or branch from center on the same row"
            }
            require(level.pattern.isNotEmpty()) { "${level.id}: pattern must not be empty" }
            require(level.pattern.all { it.hands.isNotEmpty() }) {
                "${level.id}: every pattern step must contain at least one hand"
            }
            require(level.type != LevelType.Unison || level.pattern.all { it.hands == PatternHand.entries.toSet() }) {
                "${level.id}: every unison step must contain right and left hands"
            }
            require(level.type == LevelType.Unison || level.pattern.all { it.hands.size == 1 }) {
                "${level.id}: multi-hand steps are reserved for unison levels"
            }
            require(level.rankTargets.map(RankTarget::rank).toSet() == PracticeRank.entries.toSet()) {
                "${level.id}: rank targets must define Practice, Groove, Stage, and Rockstar exactly once"
            }
            require(level.rankTargets.size == PracticeRank.entries.size) {
                "${level.id}: rank targets must not contain duplicates"
            }
            require(level.rankTargets.all { it.bpm in MIN_BPM..MAX_BPM }) {
                "${level.id}: rank target BPM must be between $MIN_BPM and $MAX_BPM"
            }
            require(level.rankTargets.all { it.repetitions > 0 }) {
                "${level.id}: rank target repetitions must be positive"
            }
            require(level.role != LevelRole.Checkpoint || level.column == LevelColumn.Center) {
                "${level.id}: checkpoints must stay on the required center path"
            }
        }
        tier.levels.groupBy(Level::row).forEach { (row, levels) ->
            require(levels.count { it.column == LevelColumn.Center } == 1) {
                "Tier ${tier.id}, row $row: every row must have one required center level"
            }
            require(levels.map(Level::column).distinct().size == levels.size) {
                "Tier ${tier.id}, row $row: a column may contain only one level"
            }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val MIN_BPM = 40
        const val MAX_BPM = 250
    }
}
