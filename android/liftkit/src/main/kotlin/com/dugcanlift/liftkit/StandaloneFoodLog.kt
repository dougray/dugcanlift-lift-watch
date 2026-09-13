package com.dugcanlift.liftkit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

interface LogStorage { fun read(): ByteArray?; fun write(bytes: ByteArray) }
class InMemoryLogStorage : LogStorage {
    private var bytes: ByteArray? = null
    override fun read() = bytes
    override fun write(bytes: ByteArray) { this.bytes = bytes }
}

@Serializable private data class StoredLog(val entries: List<LoggedFood> = emptyList(), val skipped: Int = 0)

/**
 * The retained local food log — separate from any sync queue on purpose: it is what a watch
 * that has never seen a phone has to export. Capped at 200 entries or 60 calendar days.
 */
class StandaloneFoodLog(
    private val storage: LogStorage,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object { const val MAX_ENTRIES = 200; const val MAX_AGE_DAYS = 60L }
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): StoredLog = storage.read()?.let { bytes -> runCatching { json.decodeFromString<StoredLog>(String(bytes)) }.getOrNull() } ?: StoredLog()
    private fun save(log: StoredLog) = storage.write(json.encodeToString(StoredLog.serializer(), log).toByteArray())

    val entries: List<LoggedFood> get() = capped(load().entries)
    val skippedCount: Int get() = load().skipped

    fun append(entry: LoggedFood) { val cur = load(); save(cur.copy(entries = capped(cur.entries + entry))) }
    fun recordSkipped() { val cur = load(); save(cur.copy(skipped = cur.skipped + 1)) }
    fun clear() = save(StoredLog())

    /** Removes one occurrence per requested entry; two equal logs are not both cleared by removing one. */
    fun remove(entriesToRemove: List<LoggedFood>) {
        val remaining = entriesToRemove.groupingBy { it }.eachCount().toMutableMap()
        val cur = load()
        val kept = cur.entries.filter { e ->
            val n = remaining[e] ?: 0
            if (n > 0) { remaining[e] = n - 1; false } else true
        }
        save(cur.copy(entries = kept))
    }

    /** Calendar-day cutoff, not seconds: seconds arithmetic repeats a day across a DST fall-back. */
    private fun capped(all: List<LoggedFood>): List<LoggedFood> {
        val cutoff = Instant.ofEpochSecond(clock()).atZone(zone).minusDays(MAX_AGE_DAYS).toEpochSecond()
        var kept = all.sortedBy { it.loggedAtEpochSeconds }.filter { it.loggedAtEpochSeconds >= cutoff }
        if (kept.size > MAX_ENTRIES) kept = kept.takeLast(MAX_ENTRIES)
        return kept
    }
}
