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
        var nodes = emptyList<MapNode>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "family" -> family = readFamily(reader)
                "lessons" -> lessons = reader.readArray(::readLesson)
                "map" -> nodes = readMap(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LevelCatalog.build(
            schemaVersion = schemaVersion,
            family = requireNotNull(family) { "The family package must declare a family" },
            lessons = lessons,
            nodes = nodes,
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
        var pattern = Pattern(mode = PatternMode.Repeat, steps = emptyList())
        var technique = Technique(strokeStyle = "", dynamics = "", accents = "")
        var execution = Execution(beatCount = 0)
        var rankTargets = emptyList<RankTarget>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "type" -> type = LevelType.fromStorageName(reader.nextString())
                "modifiers" -> modifiers = reader.readArray { LevelModifier.fromStorageName(it.nextString()) }.toSet()
                "pattern" -> pattern = readPattern(reader)
                "technique" -> technique = readTechnique(reader)
                "execution" -> execution = readExecution(reader)
                "rankTargets" -> rankTargets = readRankTargets(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Lesson(
            id = id,
            type = type,
            modifiers = modifiers,
            pattern = pattern,
            technique = technique,
            execution = execution,
            rankTargets = rankTargets,
        )
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

    private fun readExecution(reader: JsonReader): Execution {
        var beatCount = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "beatCount" -> beatCount = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Execution(beatCount = beatCount)
    }

    /** `rankTargets` is an object keyed by rank, not an array. */
    private fun readRankTargets(reader: JsonReader): List<RankTarget> = buildList {
        reader.beginObject()
        while (reader.hasNext()) {
            val rank = PracticeRank.fromStorageName(reader.nextName())
            var bpm = 0
            var hitsPerBeat = 0
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "bpm" -> bpm = reader.nextInt()
                    "hitsPerBeat" -> hitsPerBeat = reader.nextInt()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            add(RankTarget(rank = rank, bpm = bpm, hitsPerBeat = hitsPerBeat))
        }
        reader.endObject()
    }

    private fun readMap(reader: JsonReader): List<MapNode> {
        var nodes = emptyList<MapNode>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "nodes" -> nodes = reader.readArray(::readMapNode)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return nodes
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
}
