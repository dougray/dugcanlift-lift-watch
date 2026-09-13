package com.dugcanlift.liftkit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

interface LogStorage {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    /**
     * Set aside bytes this build could not decode, keeping them so a later version can recover them.
     * Called only immediately before something else overwrites the main slot, and only with bytes
     * that failed to decode — never with data this build understood. Implementations must not
     * discard an earlier quarantine: the oldest evidence is the one closest to the original data.
     */
    fun quarantine(bytes: ByteArray)
}

class InMemoryLogStorage : LogStorage {
    private var bytes: ByteArray? = null
    var quarantined: ByteArray? = null; private set
    override fun read() = bytes
    override fun write(bytes: ByteArray) { this.bytes = bytes }
    override fun quarantine(bytes: ByteArray) { if (quarantined == null) quarantined = bytes }
}

@Serializable private data class StoredLog(
    val entries: List<LoggedFood> = emptyList(),
    val skipped: Int = 0,
    /** Entry objects this build could not decode — a field a newer version added, say. Carried
     *  verbatim through every save, so upgrading, logging and downgrading never destroys them. */
    val unreadable: List<JsonElement> = emptyList()
)

/** What one `load()` recovered: a log, or the raw bytes when nothing at all could be made of them. */
private class Loaded(val log: StoredLog?, val undecodable: ByteArray?)

/**
 * The retained local food log — separate from any sync queue on purpose: it is what a watch
 * that has never seen a phone has to export. Capped at 200 entries; the 60-day window is a
 * read-side filter, not a delete (see [live]).
 *
 * **Nothing here deletes data it does not understand.** A write only ever persists entries this
 * build decoded plus the raw JSON of the ones it did not; a blob that is not decodable at all is
 * handed to [LogStorage.quarantine] before the fresh log replaces it. The watch's own storage is
 * the only copy of this data that exists anywhere until a code is scanned (`allowBackup="false"`),
 * so an overwrite here is permanent.
 */
class StandaloneFoodLog(
    private val storage: LogStorage,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object { const val MAX_ENTRIES = 200; const val MAX_AGE_DAYS = 60L }
    // ignoreUnknownKeys tolerates keys a newer build added; coerceInputValues tolerates a null
    // where a defaulted field is expected. Neither covers a *missing required* key, which is why
    // entries are decoded one at a time below and the failures are kept rather than dropped.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun load(): Loaded {
        val bytes = storage.read() ?: return Loaded(StoredLog(), null)
        val root = runCatching { json.parseToJsonElement(String(bytes)) as? JsonObject }.getOrNull()
            ?: return Loaded(null, bytes)
        val stored = when (val e = root["entries"]) {
            null, JsonNull -> emptyList()          // an empty log encodes as `{}`: absent is not corrupt
            is JsonArray -> e.toList()
            else -> return Loaded(null, bytes)
        }
        val good = mutableListOf<LoggedFood>()
        val bad = mutableListOf<JsonElement>()
        stored.forEach { element ->
            runCatching { json.decodeFromJsonElement(LoggedFood.serializer(), element) }
                .fold({ good += it }, { bad += element })
        }
        val carried = (root["unreadable"] as? JsonArray)?.toList() ?: emptyList()
        val skipped = (root["skipped"] as? JsonPrimitive)?.intOrNull ?: 0
        return Loaded(StoredLog(good, skipped, carried + bad), null)
    }

    /** The log as it stands, with any wholly undecodable blob set aside first — the caller is about
     *  to write, and writing over bytes we did not understand is how a log disappears. */
    private fun loadForWrite(): StoredLog {
        val loaded = load()
        loaded.undecodable?.let { storage.quarantine(it) }
        return loaded.log ?: StoredLog()
    }

    private fun save(log: StoredLog) = storage.write(json.encodeToString(StoredLog.serializer(), log).toByteArray())

    val entries: List<LoggedFood> get() = live(load().log?.entries ?: emptyList())
    val skippedCount: Int get() = load().log?.skipped ?: 0
    /** Entries still in storage but outside the 60-day window: kept, but not shown and not exported.
     *  Lets an empty Export screen say "these expired" rather than "you never logged anything". */
    val expiredCount: Int get() = (load().log?.entries ?: emptyList()).let { it.size - live(it).size }

    fun append(entry: LoggedFood) {
        val cur = loadForWrite()
        val (kept, dropped) = retained(cur.entries + entry)
        save(cur.copy(entries = kept, skipped = cur.skipped + dropped))
    }

    fun recordSkipped() { val cur = loadForWrite(); save(cur.copy(skipped = cur.skipped + 1)) }

    /** The user's own "start again": the one place data is meant to go. Undecodable bytes are still
     *  set aside rather than dropped, since the user is clearing entries, not evidence. */
    fun clear() { load().undecodable?.let { storage.quarantine(it) }; save(StoredLog()) }

    /** Removes one occurrence per requested entry; two equal logs are not both cleared by removing one. */
    fun remove(entriesToRemove: List<LoggedFood>) {
        if (entriesToRemove.isEmpty()) return
        val remaining = entriesToRemove.groupingBy { it }.eachCount().toMutableMap()
        val cur = loadForWrite()
        val kept = cur.entries.filter { e ->
            val n = remaining[e] ?: 0
            if (n > 0) { remaining[e] = n - 1; false } else true
        }
        save(cur.copy(entries = kept))
    }

    /**
     * What persistence is allowed to drop: the entry cap, and nothing else. A count is not a clock,
     * so trimming here cannot be provoked by a wrong one. The overflow is counted into `skipped`
     * rather than vanishing.
     *
     * The age window deliberately does NOT apply on the write path. A watch that boots with a stale
     * RTC after a flat battery stamps entries in 2016; when the clock corrects, those entries look
     * sixty days old although the user logged them minutes ago, and applying the window on write
     * rewrote storage with only the newest entry. A device clock that disagrees with a stored
     * timestamp is a clock fault, not an expiry.
     */
    private fun retained(all: List<LoggedFood>): Pair<List<LoggedFood>, Int> {
        val sorted = all.sortedBy { it.loggedAtEpochSeconds }
        return if (sorted.size > MAX_ENTRIES) sorted.takeLast(MAX_ENTRIES) to (sorted.size - MAX_ENTRIES)
        else sorted to 0
    }

    /**
     * The 60-day window, as a view over storage. Calendar days, not seconds: seconds arithmetic
     * repeats a day across a DST fall-back. Two clock disagreements are excluded by construction —
     * an entry stamped after `now` is never expired (the clock was ahead when it was logged), and
     * if any stored entry is newer than `now` the clock is behind the data, so no entry is judged
     * at all until it catches up.
     */
    private fun live(all: List<LoggedFood>): List<LoggedFood> {
        val sorted = all.sortedBy { it.loggedAtEpochSeconds }
        val now = clock()
        if (sorted.any { it.loggedAtEpochSeconds > now }) return sorted
        val cutoff = Instant.ofEpochSecond(now).atZone(zone).minusDays(MAX_AGE_DAYS).toEpochSecond()
        return sorted.filter { it.loggedAtEpochSeconds >= cutoff }
    }
}
