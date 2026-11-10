package com.rudimentor.app.data.levels

/**
 * A progress reset required when a map version changes the meaning of an existing rank.
 * Historical accuracy stays intact; only completion state is invalidated.
 */
internal data class RankCompletionReset(
    val levelId: String,
    val rank: PracticeRank,
)

internal data class MapMigrationStep(
    val familyId: String,
    val fromVersion: Int,
    val toVersion: Int,
    val completionResets: Set<RankCompletionReset> = emptySet(),
)

internal data class MapMigrationPlan(
    val requestedFromVersion: Int,
    val requestedToVersion: Int,
    val appliedVersion: Int,
    val completionResets: Set<RankCompletionReset>,
) {
    val isComplete: Boolean
        get() = appliedVersion >= requestedToVersion
}

internal object CourseMapMigrations {
    const val INITIAL_MAP_VERSION = 1

    private val steps = listOf(
        MapMigrationStep(
            familyId = "singles",
            fromVersion = 1,
            toVersion = 2,
        ),
        MapMigrationStep(
            familyId = "singles",
            fromVersion = 2,
            toVersion = 3,
        ),
        MapMigrationStep(
            familyId = "paradiddles",
            fromVersion = 1,
            toVersion = 2,
            completionResets = buildSet {
                listOf("paradiddles.TS-01", "paradiddles.SS-04").forEach { levelId ->
                    add(RankCompletionReset(levelId, PracticeRank.Groove))
                    add(RankCompletionReset(levelId, PracticeRank.Stage))
                }
            },
        ),
        MapMigrationStep(
            familyId = "paradiddles",
            fromVersion = 2,
            toVersion = 3,
        ),
        // Paradiddle-diddle entry ladder (decision 260): PDD-01 and PDD-02 are new, the old
        // PDD-01…05 move to PDD-03…07 unchanged. No PDD level had been passed, and the old
        // IDs are not carried over -- their orphaned keys are harmless -- so nothing resets.
        MapMigrationStep(
            familyId = "paradiddles",
            fromVersion = 3,
            toVersion = 4,
        ),
        // Transition ladder v2 (decision 249): TR-01 and TR-02 are new exercises under the old
        // IDs, so their passes no longer mean anything; TR-03 and TR-04 are new. Everything
        // else stays, including ST-08+ already passed before TR-03 became its prerequisite.
        MapMigrationStep(
            familyId = "doubles",
            fromVersion = 1,
            toVersion = 2,
            completionResets = buildSet {
                listOf("doubles.TR-01", "doubles.TR-02").forEach { levelId ->
                    PracticeRank.entries.forEach { add(RankCompletionReset(levelId, it)) }
                }
            },
        ),
    )

    /**
     * Builds a contiguous upgrade plan. A missing step never advances the stored version:
     * production keeps the learner's progress intact and retries after a fixed app update.
     * A rollback to an older bundled map is also a no-op and never downgrades stored state.
     */
    fun plan(
        familyId: String,
        fromVersion: Int,
        toVersion: Int,
    ): MapMigrationPlan {
        val safeFromVersion = maxOf(fromVersion, INITIAL_MAP_VERSION)
        if (safeFromVersion >= toVersion) {
            return MapMigrationPlan(
                requestedFromVersion = fromVersion,
                requestedToVersion = toVersion,
                appliedVersion = safeFromVersion,
                completionResets = emptySet(),
            )
        }

        var appliedVersion = safeFromVersion
        val resets = mutableSetOf<RankCompletionReset>()
        while (appliedVersion < toVersion) {
            val candidates = steps.filter {
                it.familyId == familyId && it.fromVersion == appliedVersion
            }
            if (candidates.size != 1) break
            val step = candidates.single()
            if (step.toVersion != appliedVersion + 1) break
            resets += step.completionResets
            appliedVersion = step.toVersion
        }

        return MapMigrationPlan(
            requestedFromVersion = fromVersion,
            requestedToVersion = toVersion,
            appliedVersion = appliedVersion,
            completionResets = resets,
        )
    }

    /**
     * Release-time validation for the real bundled course. Runtime stays defensive, while
     * tests fail the build if a map version, migration step, or reset target is inconsistent.
     */
    fun validationErrors(course: LevelCourse): List<String> = buildList {
        course.catalogs.forEach { (familyId, catalog) ->
            val plan = plan(familyId, INITIAL_MAP_VERSION, catalog.mapVersion)
            if (!plan.isComplete) {
                add(
                    "$familyId: missing migration step " +
                        "${plan.appliedVersion} -> ${plan.appliedVersion + 1} " +
                        "for map version ${catalog.mapVersion}",
                )
            }
        }

        steps.groupBy { it.familyId to it.fromVersion }
            .filterValues { it.size != 1 }
            .forEach { (key, duplicates) ->
                add("${key.first}: ${duplicates.size} migration steps start at version ${key.second}")
            }

        steps.forEach { step ->
            if (step.fromVersion < INITIAL_MAP_VERSION || step.toVersion != step.fromVersion + 1) {
                add(
                    "${step.familyId}: migration ${step.fromVersion} -> ${step.toVersion} " +
                        "must advance exactly one version",
                )
            }
            val catalog = course.catalogs[step.familyId]
            if (catalog == null) {
                add("${step.familyId}: migration has no bundled family catalog")
            } else if (step.toVersion <= catalog.mapVersion) {
                step.completionResets.forEach { reset ->
                    if (catalog.level(reset.levelId) == null) {
                        add(
                            "${step.familyId}: migration ${step.fromVersion} -> ${step.toVersion} " +
                                "resets unknown lesson ${reset.levelId}",
                        )
                    }
                }
            }
        }
    }
}
