package com.rudimentor.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The toolbar must stay pinned on every screen (decision 163).
 *
 * A screen that puts `verticalScroll` on the same column as its `AppToolbar` scrolls the
 * back button off the top; that is exactly how the settings, calibration and mic lab
 * screens broke. There is no instrumentation suite in this project, so the rule is guarded
 * statically: in any file that draws an `AppToolbar`, no scroll modifier may appear before
 * the toolbar call. Screens built on `ToolbarScreen` satisfy this by construction.
 */
class ToolbarLayoutTest {

    @Test
    fun `no screen scrolls its toolbar away`() {
        val ui = File("src/main/kotlin/com/rudimentor/app/ui")
        assertTrue("ui sources not found at ${ui.absolutePath}", ui.isDirectory)

        val offenders = mutableListOf<String>()
        var checked = 0
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            val toolbar = lines.indexOfFirst { it.contains("AppToolbar(") && !it.contains("fun ") }
            if (toolbar < 0) return@forEach
            checked++
            val scrollAbove = lines.take(toolbar).any {
                it.contains(".verticalScroll(") || it.contains("LazyColumn(")
            }
            if (scrollAbove) offenders += file.name
        }

        assertTrue("no screen with a toolbar was checked", checked > 0)
        assertTrue("toolbar sits inside the scrolling area: $offenders", offenders.isEmpty())
    }
}
