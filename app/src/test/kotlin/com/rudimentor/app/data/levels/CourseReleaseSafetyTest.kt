package com.rudimentor.app.data.levels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseReleaseSafetyTest {
    @Test
    fun `bundled course loads through production loader and has complete migrations`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val course = AssetCourseLoader(assets).load()

        assertEquals(setOf("singles", "doubles", "paradiddles"), course.catalogs.keys)
        val errors = CourseMapMigrations.validationErrors(course)
        assertTrue(errors.joinToString(separator = "\n"), errors.isEmpty())
    }

    /**
     * A required level nobody can play is a wall: everything behind it on the map, and every
     * map gated on it, is unreachable (the Doubles unison chain did this before decision 212).
     */
    @Test
    fun `every required level and every tab gate is playable`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val course = AssetCourseLoader(assets).load()

        val unplayableRequired = course.catalogs.values
            .flatMap { it.levels }
            .filter { it.column.required && !it.playable }
            .map { it.id }
        assertTrue("required but not playable: $unplayableRequired", unplayableRequired.isEmpty())

        val unplayableGates = course.tabs
            .mapNotNull { (it.unlock as? UnlockRule.LessonRank)?.lessonId }
            .filter { course.level(it)?.playable != true }
        assertTrue("tab gates not playable: $unplayableGates", unplayableGates.isEmpty())
    }
}
