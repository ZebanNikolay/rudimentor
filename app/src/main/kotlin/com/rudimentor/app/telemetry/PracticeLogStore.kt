package com.rudimentor.app.telemetry

import android.content.Context
import com.rudimentor.app.util.DevLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The practice log on disk: one attempt, two files.
 *
 * `attempt-<stamp>-<level>-<rank>.txt` is the short human summary the log screen
 * shows, and the matching `.jsonl` holds every event of the run so an attempt can be
 * re-scored later against different windows. The screen only ever reads the `.txt`,
 * so nothing on the device has to parse the log.
 *
 * Files live in `filesDir/telemetry` and are written on one background thread -- the
 * attempt hands over a finished [PracticeTelemetry] and returns to the poll loop
 * immediately. Sharing copies them into `cacheDir/logs`, the folder the manifest
 * already exposes through `FileProvider`.
 */
object PracticeLogStore {

    /** Attempts kept on the device. Older ones are deleted as new runs arrive. */
    const val MAX_ATTEMPTS = 60

    private const val DIRECTORY = "telemetry"
    private const val PREFIX = "attempt-"
    private const val TEXT_SUFFIX = ".txt"
    private const val JSON_SUFFIX = ".jsonl"

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PracticeLog").apply { isDaemon = true }
    }

    /** One saved attempt, as the log screen lists it. */
    data class Entry(
        val name: String,
        val title: String,
        val summary: String,
        val savedAtMs: Long,
        val textFile: File,
        val jsonFile: File?,
    )

    /**
     * Queues one finished attempt for writing. Safe to call from the poll loop or a
     * composition: everything after this happens on the log thread.
     */
    fun save(context: Context, telemetry: PracticeTelemetry) {
        val directory = File(context.filesDir, DIRECTORY)
        val header = telemetry.header
        val base = PREFIX + stamp() + "-" + slug(header.levelId) + "-" + slug(header.rank)
        val summary = telemetry.summary()
        val body = telemetry.jsonLines()
        writer.execute {
            runCatching {
                if (!directory.exists() && !directory.mkdirs()) return@runCatching
                File(directory, base + TEXT_SUFFIX).writeText(summary + "\n")
                File(directory, base + JSON_SUFFIX)
                    .writeText(body.joinToString(separator = "\n", postfix = "\n"))
                prune(directory)
            }.onFailure { error -> DevLog.error("telemetry", "could not write log", error) }
        }
    }

    /** Saved attempts, newest first. Reads the summaries only. */
    fun list(context: Context): List<Entry> {
        val directory = File(context.filesDir, DIRECTORY)
        val texts = directory.listFiles { file -> file.name.endsWith(TEXT_SUFFIX) }
            ?: return emptyList()
        return texts
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val summary = runCatching { file.readText().trimEnd() }.getOrDefault("")
                val json = File(directory, file.name.removeSuffix(TEXT_SUFFIX) + JSON_SUFFIX)
                Entry(
                    name = file.name,
                    title = summary.lineSequence().lastOrNull { it.startsWith("result ") }
                        ?: file.name,
                    summary = summary,
                    savedAtMs = file.lastModified(),
                    textFile = file,
                    jsonFile = json.takeIf { it.exists() },
                )
            }
    }

    /**
     * Copies the files of [entry] into the shared cache folder and returns them, so the
     * share sheet cannot hold a file the next attempt is rewriting.
     */
    fun exportForSharing(context: Context, entry: Entry): List<File> {
        val target = File(context.cacheDir, "logs")
        if (!target.exists() && !target.mkdirs()) return emptyList()
        return listOfNotNull(entry.textFile, entry.jsonFile).mapNotNull { source ->
            runCatching { source.copyTo(File(target, source.name), overwrite = true) }
                .onFailure { error -> DevLog.error("telemetry", "export failed", error) }
                .getOrNull()
        }
    }

    /** Deletes every saved attempt. */
    fun clear(context: Context) {
        val directory = File(context.filesDir, DIRECTORY)
        writer.execute {
            runCatching { directory.deleteRecursively() }
        }
    }

    /** Keeps the newest [MAX_ATTEMPTS] attempts and their bodies. */
    private fun prune(directory: File) {
        val texts = directory.listFiles { file -> file.name.endsWith(TEXT_SUFFIX) }
            ?: return
        if (texts.size <= MAX_ATTEMPTS) return
        texts.sortedByDescending { it.lastModified() }
            .drop(MAX_ATTEMPTS)
            .forEach { file ->
                val base = file.name.removeSuffix(TEXT_SUFFIX)
                file.delete()
                File(directory, base + JSON_SUFFIX).delete()
            }
    }

    /** File-name-safe form of a level id or a rank name. */
    private fun slug(value: String): String {
        val cleaned = value.lowercase(Locale.US)
            .map { char -> if (char.isLetterOrDigit()) char else '-' }
            .joinToString(separator = "")
            .trim('-')
        return if (cleaned.isEmpty()) "x" else cleaned.take(24)
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
