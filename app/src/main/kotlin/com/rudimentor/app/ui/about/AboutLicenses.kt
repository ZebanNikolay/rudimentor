package com.rudimentor.app.ui.about

/**
 * One row of the Open source block: what is used, under which license, and where the
 * full license text lives inside the APK.
 *
 * The list is written by hand on purpose. The project has few dependencies, and a
 * license-scanning library would only add one more entry to this very list. The full
 * texts ship as assets so the SIL Open Font License condition -- the license travels
 * together with the font files -- is met inside the installed app, not only in the
 * repository.
 */
data class LicenseEntry(
    val component: String,
    val license: String,
    val assetPath: String,
)

object AboutLicenses {
    const val APACHE_2 = "Apache License 2.0"
    const val OFL_1_1 = "SIL Open Font License 1.1"

    const val APACHE_ASSET = "licenses/apache-2.0.txt"
    const val OFL_ASSET = "licenses/ofl-1.1.txt"
    const val NOTICE_ASSET = "licenses/notice.txt"

    val entries: List<LicenseEntry> = listOf(
        LicenseEntry("RudiMentor", APACHE_2, APACHE_ASSET),
        LicenseEntry("Oboe", APACHE_2, APACHE_ASSET),
        LicenseEntry("Jetpack Compose, Material 3, AndroidX", APACHE_2, APACHE_ASSET),
        LicenseEntry("Material Components for Android", APACHE_2, APACHE_ASSET),
        LicenseEntry("kotlinx.coroutines", APACHE_2, APACHE_ASSET),
        LicenseEntry("Material Design Icons", APACHE_2, APACHE_ASSET),
        LicenseEntry("JetBrains Mono", OFL_1_1, OFL_ASSET),
        LicenseEntry("Space Grotesk", OFL_1_1, OFL_ASSET),
    )

    /** The attribution notice itself, reachable as its own row under the list. */
    val notice = LicenseEntry("Notice", "attribution", NOTICE_ASSET)
}
