package com.rudimentor.app.telemetry

import java.util.Locale

/**
 * Minimal JSON object writer for one log line.
 *
 * The log format is fixed and small, so a builder is enough and no serialization library
 * is pulled into the audio path. Shared by the attempt log and the calibration log so the
 * two bodies cannot drift into different dialects of JSONL (decision 157).
 */
internal class TelemetryJson(type: String) {

    private val out = StringBuilder(160)
    private var first = true

    init {
        out.append('{')
        text("type", type)
    }

    fun text(key: String, value: String): TelemetryJson {
        key(key)
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append(' ') else out.append(char)
            }
        }
        out.append('"')
        return this
    }

    fun int(key: String, value: Int): TelemetryJson {
        key(key)
        out.append(value)
        return this
    }

    fun bool(key: String, value: Boolean): TelemetryJson {
        key(key)
        out.append(value)
        return this
    }

    fun num(key: String, value: Float, digits: Int = DEFAULT_DIGITS): TelemetryJson {
        key(key)
        // A non-finite number is not JSON: a missed note carries NaN as its offset.
        if (value.isNaN() || value.isInfinite()) out.append("null") else {
            out.append(decimal(value, digits))
        }
        return this
    }

    fun done(): String = out.append('}').toString()

    private fun key(name: String) {
        if (!first) out.append(',')
        first = false
        out.append('"').append(name).append("\":")
    }

    companion object {
        const val DEFAULT_DIGITS = 1

        fun decimal(value: Float, digits: Int): String =
            String.format(Locale.US, "%.${digits}f", value)
    }
}
