package com.rudimentor.app.ui.about

/**
 * Where the license text of the app and of everything it borrows lives inside the APK.
 *
 * Deliberately one file behind one line, not a scrollable inventory of dependencies:
 * the app has few of them, and nobody reads a list of names. The attribution notice
 * and the full texts of both licenses are concatenated into that single asset, so the
 * Apache 2.0 and SIL Open Font License conditions -- the license travels with the
 * software and with the fonts -- are met inside the installed app.
 */
object AboutLicenses {
    const val ASSET = "licenses/licenses.txt"
}
