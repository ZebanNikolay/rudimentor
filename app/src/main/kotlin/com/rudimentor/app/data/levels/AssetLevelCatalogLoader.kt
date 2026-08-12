package com.rudimentor.app.data.levels

import android.content.res.AssetManager
import android.util.JsonReader
import java.io.InputStreamReader

class AssetLevelCatalogLoader(
    private val assets: AssetManager,
) {
    fun load(): LevelCatalog = assets.open(ASSET_NAME).use { input ->
        JsonReader(InputStreamReader(input)).use(::readCatalog).validated()
    }

    private fun readCatalog(reader: JsonReader): LevelCatalog {
        var schemaVersion = 0
        var tiers = emptyList<LevelTier>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "tiers" -> tiers = reader.readArray(::readTier)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LevelCatalog(schemaVersion = schemaVersion, tiers = tiers)
    }

    private fun readTier(reader: JsonReader): LevelTier {
        var id = ""
        var name = ""
        var levels = emptyList<Level>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "name" -> name = reader.nextString()
                "levels" -> levels = reader.readArray(::readLevel)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return LevelTier(
            id = id,
            name = name,
            levels = levels,
        )
    }

    private fun readLevel(reader: JsonReader): Level {
        var id = ""
        var row = 0
        var column = LevelColumn.Center
        var role = LevelRole.Lesson
        var type = LevelType.Steady
        var modifiers = emptySet<LevelModifier>()
        var title = ""
        var description = ""
        var pattern = emptyList<PatternStep>()
        var leadHand = LeadHand.Right
        var rankTargets = emptyList<RankTarget>()
        var prerequisiteIds = emptySet<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "row" -> row = reader.nextInt()
                "column" -> column = LevelColumn.fromStorageName(reader.nextString())
                "role" -> role = LevelRole.fromStorageName(reader.nextString())
                "type" -> type = LevelType.fromStorageName(reader.nextString())
                "modifiers" -> modifiers = reader.readArray { LevelModifier.fromStorageName(it.nextString()) }.toSet()
                "title" -> title = reader.nextString()
                "description" -> description = reader.nextString()
                "pattern" -> pattern = reader.readArray(::readPatternStep)
                "leadHand" -> leadHand = LeadHand.fromStorageName(reader.nextString())
                "rankTargets" -> rankTargets = reader.readArray(::readRankTarget)
                "prerequisiteIds" -> prerequisiteIds = reader.readArray(JsonReader::nextString).toSet()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Level(
            id = id,
            row = row,
            column = column,
            role = role,
            type = type,
            modifiers = modifiers,
            title = title,
            description = description,
            pattern = pattern,
            leadHand = leadHand,
            rankTargets = rankTargets,
            prerequisiteIds = prerequisiteIds,
        )
    }

    private fun readPatternStep(reader: JsonReader): PatternStep {
        var hands = emptySet<PatternHand>()
        var accent = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "hands" -> hands = reader.readArray { PatternHand.fromStorageName(it.nextString()) }.toSet()
                "accent" -> accent = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return PatternStep(hands = hands, accent = accent)
    }

    private fun readRankTarget(reader: JsonReader): RankTarget {
        var rank = PracticeRank.Practice
        var bpm = 0
        var repetitions = 0
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "rank" -> rank = PracticeRank.fromStorageName(reader.nextString())
                "bpm" -> bpm = reader.nextInt()
                "repetitions" -> repetitions = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RankTarget(rank = rank, bpm = bpm, repetitions = repetitions)
    }

    private fun <T> JsonReader.readArray(readItem: (JsonReader) -> T): List<T> = buildList {
        beginArray()
        while (hasNext()) add(readItem(this@readArray))
        endArray()
    }

    companion object {
        private const val ASSET_NAME = "levels.json"
    }
}
