package com.rudimentor.app

data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
) {
    val displayLabel: String = "Version $versionName · Build $versionCode"
}
