package com.rudimentor.app.data.levels

/**
 * Runtime model of the generated curriculum package
 * (`learning/course/data/generated/curricula/<id>.json`).
 *
 * The curriculum is the order of the level maps plus the gate that unlocks each one
 * (decisions 90 and 111). Before it existed the unlocks lived in prose only, so the app
 * shipped a single map; now every tab, its title and its gate come from the course data.
 */

enum class FamilyStatus(val storageName: String) {
    /** The family package ships with the app. */
    Available("available"),

    /** The map is designed but has no package yet: the tab is visible and never unlocks. */
    Planned("planned"),
    ;

    companion object {
        fun fromStorageName(value: String): FamilyStatus = entries.single { it.storageName == value }
    }
}

sealed interface UnlockRule {
    /** The first map of the curriculum: open from the first launch. */
    data object Always : UnlockRule

    /** A planned map: no progress can open it. */
    data object Never : UnlockRule

    /** Open once [lessonId] is completed at [rank]; other ranks of that lesson do not count. */
    data class LessonRank(val lessonId: String, val rank: PracticeRank) : UnlockRule
}

data class CurriculumTab(
    val id: String,
    val title: String,
    val status: FamilyStatus,
    val unlock: UnlockRule,
) {
    val available: Boolean get() = status == FamilyStatus.Available
}

data class Curriculum(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val tabs: List<CurriculumTab>,
) {
    fun tab(id: String): CurriculumTab? = tabs.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        /** Mirrors `tools/generate_curriculum.py`, so bad data fails at load instead of in the UI. */
        fun build(
            schemaVersion: Int,
            id: String,
            name: String,
            tabs: List<CurriculumTab>,
        ): Curriculum {
            require(schemaVersion == CURRENT_SCHEMA_VERSION) {
                "Unsupported curriculum package schema: $schemaVersion"
            }
            require(id.isNotBlank()) { "The curriculum must have an id" }
            require(tabs.isNotEmpty()) { "The curriculum must contain at least one tab" }
            require(tabs.map(CurriculumTab::id).distinct().size == tabs.size) { "Tab ids must be unique" }
            require(tabs.first().unlock == UnlockRule.Always) {
                "The first tab must be unlocked from the start"
            }
            require(tabs.drop(1).none { it.unlock == UnlockRule.Always }) {
                "Only the first tab may be unlocked from the start"
            }
            tabs.forEach { tab ->
                require(tab.available == (tab.unlock != UnlockRule.Never)) {
                    "${tab.id}: a planned tab must never unlock, an available tab must be reachable"
                }
            }
            return Curriculum(schemaVersion = schemaVersion, id = id, name = name, tabs = tabs)
        }
    }
}

/**
 * The curriculum together with the family packages it points at: everything the levels
 * screen needs to draw four tabs and to look a level up by id, whatever map it lives on.
 */
data class LevelCourse(
    val curriculum: Curriculum,
    val catalogs: Map<String, LevelCatalog>,
) {
    val tabs: List<CurriculumTab> get() = curriculum.tabs

    private val levelsById: Map<String, Level> = catalogs.values
        .flatMap(LevelCatalog::levels)
        .associateBy(Level::id)

    fun catalog(tabId: String): LevelCatalog? = catalogs[tabId]

    fun level(levelId: String): Level? = levelsById[levelId]

    fun family(levelId: String): Family? = catalogs[levelId.substringBefore('.')]?.family

    val levelIds: Set<String> get() = levelsById.keys

    companion object {
        fun build(curriculum: Curriculum, catalogs: Map<String, LevelCatalog>): LevelCourse {
            val available = curriculum.tabs.filter(CurriculumTab::available).map(CurriculumTab::id)
            require(catalogs.keys == available.toSet()) {
                "Every available tab needs exactly one family package: " +
                    "expected $available, loaded ${catalogs.keys.sorted()}"
            }
            catalogs.forEach { (tabId, catalog) ->
                require(catalog.family.id == tabId) {
                    "$tabId: the package declares family '${catalog.family.id}'"
                }
            }
            val known = catalogs.values.flatMap { it.levels.map(Level::id) }.toSet()
            curriculum.tabs.mapNotNull { it.unlock as? UnlockRule.LessonRank }.forEach { rule ->
                require(rule.lessonId in known) {
                    "The gate lesson ${rule.lessonId} is not part of any loaded family"
                }
            }
            return LevelCourse(curriculum = curriculum, catalogs = catalogs)
        }
    }
}
