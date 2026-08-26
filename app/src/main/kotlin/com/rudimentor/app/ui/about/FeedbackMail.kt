package com.rudimentor.app.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rudimentor.app.BuildInfo
import java.net.URLEncoder
import java.util.Locale

/**
 * The feedback channel: one mailbox, no form, no network call of our own.
 *
 * `ACTION_SENDTO` with a `mailto:` URI is the only variant that reaches mail apps
 * and nothing else -- `ACTION_SEND` would offer messengers and file managers too,
 * and would not be answerable by one person reading one inbox.
 */
object FeedbackMail {
    const val ADDRESS = "rudimentor.app@gmail.com"

    /** Subject carries the build, so a report is never about an unknown version. */
    fun subject(buildInfo: BuildInfo): String =
        "RudiMentor feedback · ${buildInfo.versionName} (${buildInfo.versionCode})"

    /**
     * Body: empty room at the top for the person writing, and a short technical
     * block below it. Everything in the block is device metadata already visible
     * to any app -- no identifiers, no practice data, nothing collected in advance.
     */
    fun body(buildInfo: BuildInfo, prompt: String): String = buildString {
        append(prompt)
        append("\n\n\n\n---\n")
        append("App: ${buildInfo.versionName} (${buildInfo.versionCode})\n")
        append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("Locale: ${Locale.getDefault()}\n")
    }

    /**
     * The whole letter lives in the `mailto:` URI, subject and body included.
     *
     * Extras alone are not enough: several mail apps (Gmail among them) open a
     * `mailto:` intent by parsing the URI and ignore `EXTRA_SUBJECT` / `EXTRA_TEXT`,
     * which is why the first version of this screen opened an empty draft. Both
     * channels are filled now -- whichever one the mail app reads, the draft arrives
     * prefilled.
     */
    fun mailto(buildInfo: BuildInfo, prompt: String): String =
        mailtoWith(subject(buildInfo), body(buildInfo, prompt))

    /** Split out from [mailto] so the encoding is testable without device metadata. */
    internal fun mailtoWith(subject: String, body: String): String =
        "mailto:$ADDRESS?subject=${encode(subject)}&body=${encode(body)}"

    /** Percent-encoding for a URI query: spaces are %20 here, never '+'. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun intent(buildInfo: BuildInfo, prompt: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse(mailto(buildInfo, prompt))).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject(buildInfo))
            putExtra(Intent.EXTRA_TEXT, body(buildInfo, prompt))
        }

    /**
     * Opens a mail app. Returns false when the device has none -- the caller then
     * copies the address instead of leaving the tap without an answer.
     */
    fun send(context: Context, buildInfo: BuildInfo, prompt: String): Boolean =
        try {
            context.startActivity(intent(buildInfo, prompt))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}

/** Opens a URL in a browser. Same contract as [FeedbackMail.send]. */
fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
