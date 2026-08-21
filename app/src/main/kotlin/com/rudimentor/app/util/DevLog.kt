package com.rudimentor.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Small on-device log so a field session can be reported back without a cable.
 *
 * Every entry goes to Logcat and to a single append-only file in `filesDir`, which
 * survives process death: an uncaught exception is written before the process
 * goes down, so the crash is still there on the next launch. The developer screen
 * reads [snapshot] and shares [exportTo].
 *
 * Writes happen on one background thread, so logging from the audio poll loop or
 * from a composition never touches the file system on the caller's thread.
 */
object DevLog {
    private const val TAG = "RudiMentor"
    private const val FILE_NAME = "rudimentor-log.txt"

    /** Above this the file is halved, so a long session cannot fill the device. */
    private const val MAX_BYTES = 512L * 1024L

    /** How much of the tail the developer screen keeps in memory. */
    private const val MEMORY_LINES = 600

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DevLog").apply { isDaemon = true }
    }
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val recent = ArrayDeque<String>()

    @Volatile
    private var file: File? = null

    /** Call once from `Application`/`Activity` start, before anything is logged. */
    fun install(context: Context, sessionLabel: String) {
        if (file != null) return
        file = File(context.filesDir, FILE_NAME)
        installCrashHandler()
        log("app", "--- session start · $sessionLabel ---")
    }

    fun log(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        append("${now()} $tag: $message")
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        val trace = throwable?.let { "\n${stackTrace(it)}" } ?: ""
        append("${now()} $tag: ERROR $message$trace")
    }

    /** The tail of the log, oldest first. Safe to read from a composition. */
    fun snapshot(): List<String> = synchronized(recent) { recent.toList() }

    /**
     * Copies the log into [dir] under a timestamped name and returns it, so the
     * shared file cannot change while the share sheet has it open.
     */
    fun exportTo(dir: File): File? {
        val source = file?.takeIf { it.exists() } ?: return null
        flush()
        if (!dir.exists() && !dir.mkdirs()) return null
        val name = "rudimentor-log-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        val target = File(dir, name)
        return runCatching {
            source.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }

    fun clear() {
        synchronized(recent) { recent.clear() }
        writer.execute { runCatching { file?.writeText("") } }
    }

    /** Blocks until queued lines are on disk. Only used before sharing the file. */
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
            if (target.length() > MAX_BYTES) halve(target)
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
                    "${now()} crash: FATAL on ${thread.name}\n${stackTrace(error)}\n"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun stackTrace(throwable: Throwable): String {
        val out = StringWriter()
        PrintWriter(out).use(throwable::printStackTrace)
        return out.toString().trimEnd()
    }

    private fun now(): String = stamp.format(Date())
}
