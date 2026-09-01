package com.rudimentor.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.rudimentor.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The on-device log, in two levels.
 *
 * [event] and [error] survive into the release build: they record what happened to the
 * device and to the app -- which audio route is open, what latency the engine reported,
 * why a round measured nothing, an uncaught exception. That is the material a stranger's
 * bug report needs, and the About screen can attach it to a feedback mail.
 *
 * [trace] exists only in the debug build. It records what the person was doing --
 * screens, level ids, results, the per-second clock diagnostic -- which is useful on
 * the bench and is nobody's business in a shipped app. The call takes a lambda and is
 * inline behind `BuildConfig.DEBUG`, so in release neither the message nor the string
 * that builds it survives R8.
 *
 * Both levels go to Logcat and to a single append-only file in `filesDir`, which
 * survives process death: an uncaught exception is written before the process goes down,
 * so the crash is still there on the next launch. Trace lines are marked with [TRACE_MARK]
 * so [diagnosticText] can leave them out -- what the debug build offers to send is
 * exactly what a release build would send.
 *
 * Writes happen on one background thread, so logging from the audio poll loop or from a
 * composition never touches the file system on the caller's thread.
 */
object AppLog {
    private const val TAG = "RudiMentor"
    private const val FILE_NAME = "rudimentor-log.txt"

    /** Prefixes the tag of a debug-only line. Never appears in a release file. */
    const val TRACE_MARK = "~"

    /**
     * Above this the file is halved, so a long session cannot fill the device. The
     * release build writes events only, and a smaller cap keeps the tail recent.
     */
    private val maxBytes: Long = if (BuildConfig.DEBUG) 512L * 1024L else 128L * 1024L

    /** How much of the tail the developer screen keeps in memory. */
    private const val MEMORY_LINES = 600

    /** How much of a crash report the startup screen shows at most. */
    private const val MAX_REPORT_CHARS = 20_000

    /** How much of the log a feedback mail carries: enough to read, small enough to send. */
    const val MAX_MAIL_CHARS = 64_000

    private const val CRASH_MARKER = "crash: FATAL"
    private const val ACKNOWLEDGED_MARKER = "report acknowledged"

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLog").apply { isDaemon = true }
    }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val recent = ArrayDeque<String>()

    @Volatile
    private var file: File? = null

    /** Build and device of the running session, stamped into a crash record. */
    @Volatile
    private var session: String = "unknown build"

    /** Call once from `Application`/`Activity` start, before anything is logged. */
    fun install(context: Context, sessionLabel: String) {
        if (file != null) return
        file = File(context.filesDir, FILE_NAME)
        session = sessionLabel
        installCrashHandler()
        event("app", "--- session start · $sessionLabel ---")
        event("app", device())
    }

    /**
     * A line worth keeping in a shipped build: hardware, audio routing, measured
     * latency, a refused engine, an applied calibration. Nothing about which level
     * the person played or how well they did -- that is [trace].
     */
    fun event(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        append("${now()} $tag: $message")
    }

    /**
     * A line for the bench only. Compiled out of the release build together with the
     * expression that builds the message.
     */
    inline fun trace(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) traceLine(tag, message())
    }

    /** The body of [trace]. Public only because an inline function needs it to be. */
    @PublishedApi
    internal fun traceLine(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        append("${now()} $TRACE_MARK$tag: $message")
    }

    /** Always kept: a failure explains a report even when nothing else does. */
    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        val trace = throwable?.let { "\n${stackTrace(it)}" } ?: ""
        append("${now()} $tag: ERROR $message$trace")
    }

    /** The tail of the log, oldest first. Safe to read from a composition. */
    fun snapshot(): List<String> = synchronized(recent) { recent.toList() }

    /**
     * What a feedback mail carries: the event lines only, newest [maxChars] of them.
     * Reading the file rather than [recent] keeps the previous session's crash in.
     */
    fun diagnosticText(maxChars: Int = MAX_MAIL_CHARS): String {
        val source = file?.takeIf { it.exists() } ?: return ""
        flush()
        val lines = runCatching { source.readLines() }.getOrNull() ?: return ""
        return diagnosticOf(lines, maxChars)
    }

    /**
     * Split out of [diagnosticText] so the filtering is testable without a device: drops
     * the debug-only lines and keeps the newest [maxChars] characters, cut on a line.
     */
    internal fun diagnosticOf(lines: List<String>, maxChars: Int): String {
        val kept = lines.filterNot { isTrace(it) }
        val text = kept.joinToString(separator = "\n")
        if (text.length <= maxChars) return text
        val cut = text.length - maxChars
        val start = text.indexOf('\n', cut).let { if (it < 0) cut else it + 1 }
        return text.substring(start)
    }

    /** `MM-dd HH:mm:ss.SSS ~tag: message` -- the mark sits on the tag, after the stamp. */
    private fun isTrace(line: String): Boolean {
        val space = line.indexOf(' ', startIndex = line.indexOf(' ') + 1)
        if (space < 0 || space + 1 >= line.length) return false
        return line[space + 1] == TRACE_MARK[0]
    }

    /**
     * Copies the diagnostic log into [dir] under a timestamped name and returns it, so
     * the shared file cannot change while the mail app has it open.
     */
    fun exportDiagnostics(dir: File): File? {
        val text = diagnosticText().takeIf { it.isNotBlank() } ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        val name = "rudimentor-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        return runCatching {
            File(dir, name).apply { writeText(text) }
        }.getOrNull()
    }

    /** The whole file, debug lines included. The developer screen shares this one. */
    fun exportTo(dir: File): File? {
        val source = file?.takeIf { it.exists() } ?: return null
        flush()
        if (!dir.exists() && !dir.mkdirs()) return null
        val name = "rudimentor-full-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val target = File(dir, name)
        return runCatching {
            source.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }

    /**
     * The tail of the log file starting at the last fatal crash, or `null` when the
     * last crash has already been acknowledged (or there was none). Read straight from
     * the file, so a crash of the previous process is still reportable after a restart.
     */
    fun pendingCrash(): String? {
        val source = file?.takeIf { it.exists() } ?: return null
        val lines = runCatching { source.readLines() }.getOrNull() ?: return null
        val start = lines.indexOfLast { it.contains(CRASH_MARKER) }
        if (start < 0) return null
        val tail = lines.subList(start, lines.size)
        if (tail.any { it.contains(ACKNOWLEDGED_MARKER) }) return null
        return tail.joinToString(separator = "\n").takeLast(MAX_REPORT_CHARS)
    }

    /** Marks the pending crash as seen, so the next launch opens the app again. */
    fun acknowledgeCrash() {
        event("crash", ACKNOWLEDGED_MARKER)
        flush()
    }

    fun clear() {
        synchronized(recent) { recent.clear() }
        writer.execute { runCatching { file?.writeText("") } }
    }

    /** Blocks until queued lines are on disk. Only used before reading or sharing. */
    private fun flush() {
        val done = java.util.concurrent.CountDownLatch(1)
        writer.execute { done.countDown() }
        runCatching { done.await(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun append(line: String) {
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > MEMORY_LINES) recent.removeFirst()
        }
        val target = file ?: return
        writer.execute { writeLine(target, line) }
    }

    private fun writeLine(target: File, line: String) {
        runCatching {
            target.appendText(line + "\n")
            if (target.length() > maxBytes) halve(target)
        }
    }

    /** Keeps the newer half of the file: the recent session is what matters. */
    private fun halve(target: File) {
        val lines = target.readLines()
        val kept = lines.drop(lines.size / 2)
        target.writeText(kept.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written straight through: the process is about to die and the
            // background writer may never get scheduled again.
            runCatching {
                file?.appendText(
                    "${now()} crash: FATAL on ${thread.name} · $session\n${stackTrace(error)}\n"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Device metadata every app can read anyway, and the first thing a report needs. */
    private fun device(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT})"

    private fun stackTrace(throwable: Throwable): String {
        val out = StringWriter()
        PrintWriter(out).use(throwable::printStackTrace)
        return out.toString().trimEnd()
    }

    private fun now(): String = stamp.format(Date())
}
