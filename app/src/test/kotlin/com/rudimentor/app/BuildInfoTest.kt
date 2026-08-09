package com.rudimentor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInfoTest {
    @Test
    fun `display label identifies both the installed version and build`() {
        val buildInfo = BuildInfo(versionName = "0.1.0-dev.4", versionCode = 4)

        assertEquals("Version 0.1.0-dev.4 · Build 4", buildInfo.displayLabel)
    }
}
