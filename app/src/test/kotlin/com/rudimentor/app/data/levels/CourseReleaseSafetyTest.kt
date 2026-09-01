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
}
