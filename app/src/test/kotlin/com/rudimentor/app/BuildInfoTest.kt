package com.rudimentor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildInfoTest {
    @Test
    fun `display label identifies both the installed version and build`() {
        val buildInfo = BuildInfo(versionName = "0.1.0-dev.7", versionCode = 7)

        assertEquals("Version 0.1.0-dev.7 · Build 7", buildInfo.displayLabel)
    }
}
