package com.rudimentor.app.stats

/**
 * Flat key/value form of the ledger, the shape a Preferences DataStore holds. Pure Kotlin so
 * the round trip is testable without Android.
 *
 * Layout (schema 1):
 *
 * ```
 * stats.schema                 Int     PlayStats.SCHEMA
 * stats.since.ms / .day        Long    start of tracking
 * stats.since.build            Int     versionCode that started it
 * stats.lastDay                Long    clock guard, see PlayStatsLedger.resolveDay
 * stats.total.<stat>           Long    lifetime counter, PlayStat.key
 * stats.day.<epochDay>         String  "ch.completed=3;pr.hits=812;..."  (non-zero daily stats)
 * stats.variant.<level>:<rank> String  "completed,failedBeforePass,passedOnTake,crownedOnTake,historyComplete"
 * stats.modes.<level>          Int     PlayStatsRules mode bits
 * ```
 *
 * Unknown keys are ignored on read, so a counter added by a later build survives in storage
 * untouched; a malformed entry is skipped instead of failing the whole ledger.
 */
object PlayStatsCodec {
    const val PREFIX = "stats."
    const val SCHEMA = "stats.schema"
    const val SINCE_MS = "stats.since.ms"
    const val SINCE_DAY = "stats.since.day"
    const val SINCE_BUILD = "stats.since.build"
    const val LAST_DAY = "stats.lastDay"
    const val TOTAL = "stats.total."
    const val DAY = "stats.day."
    const val VARIANT = "stats.variant."
    const val MODES = "stats.modes."

    /** Reads whatever is stored; an empty map is "no data yet", schema included. */
    fun decode(values: Map<String, Any?>): PlayStats {
        val totals = HashMap<PlayStat, Long>()
        val days = HashMap<Long, Map<PlayStat, Long>>()
        val variants = HashMap<String, VariantRecord>()
        val modes = HashMap<String, Int>()
        values.forEach { (key, value) ->
            when {
                key.startsWith(TOTAL) -> {
                    val stat = PlayStat.forKey(key.removePrefix(TOTAL)) ?: return@forEach
                    (value as? Number)?.toLong()?.takeIf { it > 0 }?.let { totals[stat] = it }
                }
                key.startsWith(DAY) -> {
                    val day = key.removePrefix(DAY).toLongOrNull() ?: return@forEach
                    val bucket = decodeBucket(value as? String ?: return@forEach)
                    if (bucket.isNotEmpty()) days[day] = bucket
                }
                key.startsWith(VARIANT) -> {
                    val record = decodeVariant(value as? String ?: return@forEach) ?: return@forEach
                    variants[key.removePrefix(VARIANT)] = record
                }
                key.startsWith(MODES) -> {
                    (value as? Number)?.toInt()?.let { modes[key.removePrefix(MODES)] = it }
                }
            }
        }
        return PlayStats(
            schema = (values[SCHEMA] as? Number)?.toInt() ?: PlayStats.SCHEMA,
            trackingSinceMs = (values[SINCE_MS] as? Number)?.toLong(),
            trackingSinceDay = (values[SINCE_DAY] as? Number)?.toLong(),
            trackingSinceBuild = (values[SINCE_BUILD] as? Number)?.toInt(),
            totals = totals,
            days = days,
            variants = variants,
            levelModes = modes,
            lastDay = (values[LAST_DAY] as? Number)?.toLong(),
        )
    }

    /** Every key of [stats]; values are Int, Long or String. */
    fun encode(stats: PlayStats): Map<String, Any> = buildMap {
        put(SCHEMA, stats.schema)
        stats.trackingSinceMs?.let { put(SINCE_MS, it) }
        stats.trackingSinceDay?.let { put(SINCE_DAY, it) }
        stats.trackingSinceBuild?.let { put(SINCE_BUILD, it) }
        stats.lastDay?.let { put(LAST_DAY, it) }
        stats.totals.forEach { (stat, value) -> put(TOTAL + stat.key, value) }
        stats.days.forEach { (day, bucket) -> put(DAY + day, encodeBucket(bucket)) }
        stats.variants.forEach { (key, record) -> put(VARIANT + key, encodeVariant(record)) }
        stats.levelModes.forEach { (levelId, bits) -> put(MODES + levelId, bits) }
    }

    internal fun encodeBucket(bucket: Map<PlayStat, Long>): String = bucket.entries
        .filter { it.value > 0 }
        .sortedBy { it.key.ordinal }
        .joinToString(";") { "${it.key.key}=${it.value}" }

    internal fun decodeBucket(text: String): Map<PlayStat, Long> = text.split(';')
        .mapNotNull { pair ->
            val split = pair.indexOf('=')
            if (split <= 0) return@mapNotNull null
            val stat = PlayStat.forKey(pair.substring(0, split)) ?: return@mapNotNull null
            val value = pair.substring(split + 1).toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            stat to value
        }
        .toMap()

    internal fun encodeVariant(record: VariantRecord): String = listOf(
        record.completed,
        record.failedBeforePass,
        record.passedOnTake,
        record.crownedOnTake,
        if (record.historyComplete) 1 else 0,
    ).joinToString(",")

    internal fun decodeVariant(text: String): VariantRecord? {
        val parts = text.split(',').map { it.toIntOrNull() ?: return null }
        if (parts.size < 5) return null
        return VariantRecord(
            completed = parts[0],
            failedBeforePass = parts[1],
            passedOnTake = parts[2],
            crownedOnTake = parts[3],
            historyComplete = parts[4] != 0,
        )
    }
}
