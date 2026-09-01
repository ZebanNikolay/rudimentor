package com.rudimentor.app.data.levels

import android.content.res.AssetManager
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * Reads a generated family package from `assets/families/<id>.json`.
 * The JSON layout is owned by the course pipeline; this reader follows it field for field.
 */
class AssetLevelCatalogLoader(
    private val assets: AssetManager,
    private val familyId: String = DEFAULT_FAMILY_ID,
) {
    fun load(): LevelCatalog = assets.open(assetName(familyId)).use { input ->
        JsonReader(InputStreamReader(input)).use(::readPackage)
    }

    private fun readPackage(reader: JsonReader): LevelCatalog {
        var schemaVersion = 0
        var family: Family? = null
        var lessons = emptyList<Lesson>()
        var map = MapPackage()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "family" -> family = readFamily(reader)
                "lessons" -> lessons = reader.readArray(::readLesson)
                "map" -> map = readMap(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LevelCatalog.build(
            schemaVersion = schemaVersion,
            mapVersion = map.version,
            family = requireNotNull(family) { "The family package must declare a family" },
            lessons = lessons,
            nodes = map.nodes,
        )
    }

    private fun readFamily(reader: JsonReader): Family {
        var id = ""
        var name = ""
        var description = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "name" -> name = reader.nextString()
                "description" -> description = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Family(id = id, name = name, description = description)
    }

    private fun readLesson(reader: JsonReader): Lesson {
        var id = ""
        var type = LevelType.Steady
        var modifiers = emptySet<LevelModifier>()
        var pattern: Pattern? = null
        var transitionPlan: TransitionPlan? = null
        var weakFocus: WeakFocus? = null
        var technique = Technique(strokeStyle = "", dynamics = "", accents = "")
        var execution = Execution()
        var rankTargets = emptyList<RankTarget>()
        var midCycleSwitch = false
        var intentionalRollback: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "type" -> type = LevelType.fromStorageName(reader.nextString())
                "modifiers" -> modifiers = reader.readArray { LevelModifier.fromStorageName(it.nextString()) }.toSet()
                "pattern" -> pattern = readPattern(reader)
                "transitionPlan" -> transitionPlan = readTransitionPlan(reader)
                "weakFocus" -> weakFocus = readWeakFocus(reader)
                "technique" -> technique = readTechnique(reader)
                "execution" -> execution = readExecution(reader)
                "rankTargets" -> rankTargets = readRankTargets(reader)
                "midCycleSwitch" -> midCycleSwitch = reader.nextBoolean()
                "intentionalRollback" -> intentionalRollback = readAnnotationReason(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Lesson(
            id = id,
            type = type,
            modifiers = modifiers,
            pattern = pattern,
            transitionPlan = transitionPlan,
            weakFocus = weakFocus,
            technique = technique,
            execution = execution,
            rankTargets = rankTargets,
            midCycleSwitch = midCycleSwitch,
            intentionalRollback = intentionalRollback,
        )
    }

    /** An annotation is an object with a mandatory human-readable `reason`. */
    private fun readAnnotationReason(reader: JsonReader): String {
        var reason = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "reason" -> reason = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return reason
    }

    private fun readPattern(reader: JsonReader): Pattern {
        var mode = PatternMode.Repeat
        var steps = emptyList<PatternStep>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "mode" -> mode = PatternMode.fromStorageName(reader.nextString())
                "steps" -> steps = reader.readArray(::readPatternStep)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Pattern(mode = mode, steps = steps)
    }

    private fun readPatternStep(reader: JsonReader): PatternStep {
        var hands = emptySet<PatternHand>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "hands" -> hands = reader.readArray { PatternHand.fromStorageName(it.nextString()) }.toSet()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PatternStep(hands = hands)
    }

    private fun readTechnique(reader: JsonReader): Technique {
        var strokeStyle = ""
        var dynamics = ""
        var accents = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "strokeStyle" -> strokeStyle = reader.nextString()
                "dynamics" -> dynamics = reader.nextString()
                "accents" -> accents = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Technique(strokeStyle = strokeStyle, dynamics = dynamics, accents = accents)
    }

    private fun readTransitionPlan(reader: JsonReader): TransitionPlan {
        var repeatCount = 0
        var phases = emptyList<TransitionPhase>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "repeatCount" -> repeatCount = reader.nextInt()
                "phases" -> phases = reader.readArray(::readTransitionPhase)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TransitionPlan(repeatCount = repeatCount, phases = phases)
    }

    private fun readTransitionPhase(reader: JsonReader): TransitionPhase {
        var beatCount = 0
        var pattern = Pattern(mode = PatternMode.Repeat, steps = emptyList())
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "beatCount" -> beatCount = reader.nextInt()
                "pattern" -> pattern = readPattern(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TransitionPhase(beatCount = beatCount, pattern = pattern)
    }

    private fun readWeakFocus(reader: JsonReader): WeakFocus {
        var strategy = WeakStrategy.WeakLead
        var authoredWeakHand = PatternHand.Left
        var adaptToUser = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "strategy" -> strategy = WeakStrategy.fromStorageName(reader.nextString())
                "authoredWeakHand" -> authoredWeakHand = PatternHand.fromStorageName(reader.nextString())
                "adaptToUser" -> adaptToUser = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return WeakFocus(
            strategy = strategy,
            authoredWeakHand = authoredWeakHand,
            adaptToUser = adaptToUser,
        )
    }

    private fun readExecution(reader: JsonReader): Execution {
        var beatCount: Int? = null
        var durationSeconds: Int? = null
        var completionMode: CompletionMode? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "beatCount" -> beatCount = reader.nextInt()
                "durationSeconds" -> durationSeconds = reader.nextInt()
                "completionMode" -> completionMode = CompletionMode.fromStorageName(reader.nextString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Execution(
            beatCount = beatCount,
            durationSeconds = durationSeconds,
            completionMode = completionMode,
        )
    }

    /**
     * `rankTargets` is an object keyed by rank, not an array. A planned target carries no
     * scalar tempo or density, so both are taken from the entry point of its plan — the
     * value the practice engine starts at.
     */
    private fun readRankTargets(reader: JsonReader): List<RankTarget> = buildList {
        reader.beginObject()
        while (reader.hasNext()) {
            val rank = PracticeRank.fromStorageName(reader.nextName())
            var bpm: Int? = null
            var hitsPerBeat: Int? = null
            var subdivisionPlan: SubdivisionPlan? = null
            var tempoRampPlan: TempoRampPlan? = null
            var densityException: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "bpm" -> bpm = reader.nextInt()
                    "hitsPerBeat" -> hitsPerBeat = reader.nextInt()
                    "subdivisionPlan" -> subdivisionPlan = readSubdivisionPlan(reader)
                    "tempoRampPlan" -> tempoRampPlan = readTempoRampPlan(reader)
                    "densityException" -> densityException = readAnnotationReason(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            add(
                RankTarget(
                    rank = rank,
                    bpm = bpm ?: tempoRampPlan?.phases?.firstOrNull()?.bpm ?: 0,
                    hitsPerBeat = hitsPerBeat ?: subdivisionPlan?.hitsPerBeat?.firstOrNull() ?: 0,
                    subdivisionPlan = subdivisionPlan,
                    tempoRampPlan = tempoRampPlan,
                    densityException = densityException,
                ),
            )
        }
        reader.endObject()
    }

    private fun readSubdivisionPlan(reader: JsonReader): SubdivisionPlan {
        var blockBeats = 0
        var hitsPerBeat = emptyList<Int>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "blockBeats" -> blockBeats = reader.nextInt()
                "hitsPerBeat" -> hitsPerBeat = reader.readArray(JsonReader::nextInt)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return SubdivisionPlan(blockBeats = blockBeats, hitsPerBeat = hitsPerBeat)
    }

    private fun readTempoRampPlan(reader: JsonReader): TempoRampPlan {
        var mode = ""
        var direction = ""
        var phases = emptyList<TempoRampPhase>()
        var repeatCount = 1
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "mode" -> mode = reader.nextString()
                "direction" -> direction = reader.nextString()
                "phases" -> phases = reader.readArray(::readTempoRampPhase)
                "repeatCount" -> repeatCount = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TempoRampPlan(
            mode = mode,
            direction = direction,
            phases = phases,
            repeatCount = repeatCount,
        )
    }

    private fun readTempoRampPhase(reader: JsonReader): TempoRampPhase {
        var bpm = 0
        var beatCount = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "bpm" -> bpm = reader.nextInt()
                "beatCount" -> beatCount = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return TempoRampPhase(bpm = bpm, beatCount = beatCount)
    }

    private fun readMap(reader: JsonReader): MapPackage {
        var version = 0
        var nodes = emptyList<MapNode>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "version" -> version = reader.nextInt()
                "nodes" -> nodes = reader.readArray(::readMapNode)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return MapPackage(version = version, nodes = nodes)
    }

    private fun readMapNode(reader: JsonReader): MapNode {
        var lessonId = ""
        var column = LevelColumn.Center
        var prerequisites = emptySet<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "lessonId" -> lessonId = reader.nextString()
                "column" -> column = LevelColumn.fromStorageName(reader.nextString())
                "prerequisites" -> prerequisites = reader.readArray(JsonReader::nextString).toSet()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return MapNode(lessonId = lessonId, column = column, prerequisites = prerequisites)
    }

    private fun <T> JsonReader.readArray(readItem: (JsonReader) -> T): List<T> = buildList {
        beginArray()
        while (hasNext()) add(readItem(this@readArray))
        endArray()
    }

    companion object {
        const val DEFAULT_FAMILY_ID = "singles"

        private fun assetName(familyId: String) = "families/$familyId.json"
    }

    private data class MapPackage(
        val version: Int = 0,
        val nodes: List<MapNode> = emptyList(),
    )
}
