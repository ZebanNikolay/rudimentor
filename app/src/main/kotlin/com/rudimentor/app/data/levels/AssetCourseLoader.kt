package com.rudimentor.app.data.levels

import android.content.res.AssetManager
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * Reads the whole course from the assets: the curriculum package
 * (`assets/curricula/<id>.json`) and one family package per available tab.
 *
 * All packages are read at startup. Together they are a few dozen kilobytes of text, and
 * the levels screen needs every map anyway: a gate on one tab is a completed lesson on another.
 */
class AssetCourseLoader(
    private val assets: AssetManager,
    private val curriculumId: String = DEFAULT_CURRICULUM_ID,
) {
    fun load(): LevelCourse {
        val curriculum = assets.open(assetName(curriculumId)).use { input ->
            JsonReader(InputStreamReader(input)).use(::readCurriculum)
        }
        val catalogs = curriculum.tabs
            .filter(CurriculumTab::available)
            .associate { tab -> tab.id to AssetLevelCatalogLoader(assets, tab.id).load() }
        return LevelCourse.build(curriculum = curriculum, catalogs = catalogs)
    }

    private fun readCurriculum(reader: JsonReader): Curriculum {
        var schemaVersion = 0
        var id = ""
        var name = ""
        var tabs = emptyList<CurriculumTab>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "schemaVersion" -> schemaVersion = reader.nextInt()
                "curriculum" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "id" -> id = reader.nextString()
                            "name" -> name = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "tabs" -> tabs = reader.readArray(::readTab)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return Curriculum.build(schemaVersion = schemaVersion, id = id, name = name, tabs = tabs)
    }

    private fun readTab(reader: JsonReader): CurriculumTab {
        var id = ""
        var title = ""
        var shortTitle: String? = null
        var sticking: String? = null
        var status = FamilyStatus.Planned
        var unlock: UnlockRule = UnlockRule.Never
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "title" -> title = reader.nextString()
                "shortTitle" -> shortTitle = reader.nextString()
                "sticking" -> sticking = reader.nextString()
                "status" -> status = FamilyStatus.fromStorageName(reader.nextString())
                "unlock" -> unlock = readUnlock(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return CurriculumTab(
            id = id,
            title = title,
            status = status,
            unlock = unlock,
            sticking = sticking,
            shortTitle = shortTitle,
        )
    }

    private fun readUnlock(reader: JsonReader): UnlockRule {
        var type = ""
        var lessonId: String? = null
        var rank: PracticeRank? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> type = reader.nextString()
                "lessonId" -> lessonId = reader.nextString()
                "rank" -> rank = PracticeRank.fromStorageName(reader.nextString())
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return when (type) {
            "always" -> UnlockRule.Always
            "never" -> UnlockRule.Never
            "lesson_rank" -> UnlockRule.LessonRank(
                lessonId = requireNotNull(lessonId) { "A lesson gate must name a lesson" },
                rank = requireNotNull(rank) { "A lesson gate must name a rank" },
            )
            else -> throw IllegalArgumentException("Unknown unlock type: $type")
        }
    }

    private fun <T> JsonReader.readArray(readItem: (JsonReader) -> T): List<T> = buildList {
        beginArray()
        while (hasNext()) add(readItem(this@readArray))
        endArray()
    }

    companion object {
        const val DEFAULT_CURRICULUM_ID = "main"

        private fun assetName(curriculumId: String) = "curricula/$curriculumId.json"
    }
}
