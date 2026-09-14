package com.dugcanlift.liftkit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

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
    /**
     * How many blobs handed to [quarantine] were discarded because a corruption was already
     * parked there. First-wins keeps only one blob for the user's own recovery; this is the
     * counter that keeps the ones that didn't win visible instead of erasing them with no trace.
     */
    fun quarantineDiscardedCount(): Int
}

class InMemoryLogStorage : LogStorage {
    private var bytes: ByteArray? = null
    var quarantined: ByteArray? = null; private set
    private var discarded = 0
    override fun read() = bytes
    override fun write(bytes: ByteArray) { this.bytes = bytes }
    override fun quarantine(bytes: ByteArray) { if (quarantined == null) quarantined = bytes else discarded++ }
    override fun quarantineDiscardedCount(): Int = discarded
}

@Serializable private data class StoredLog(
    val entries: List<LoggedFood> = emptyList(),
    val skipped: Int = 0,
    /** Entry objects this build could not decode — a field a newer version added, say. Carried
     *  verbatim through every save, so upgrading, logging and downgrading never destroys them.
     *  Retried on every load (see [StandaloneFoodLog.load]) and bounded at [StandaloneFoodLog.MAX_UNREADABLE_ENTRIES]. */
    val unreadable: List<JsonElement> = emptyList(),
    /** Unreadable entries dropped for exceeding the cap above — counted, never silent. */
    val unreadableDropped: Int = 0
)

/** What one `load()` recovered: a log, or the raw bytes when nothing at all could be made of them. */
private class Loaded(val log: StoredLog?, val undecodable: ByteArray?)

/**
 * The retained local food log — separate from any sync queue on purpose: it is what a watch
 * that has never seen a phone has to export. Capped at 200 entries, and by nothing else:
 * settled 2026-09-13, age neither deletes nor hides. A watch out of contact for two months
 * still hands over everything it recorded, and no code path here reads the clock to decide
 * what a user may see or export.
 *
 * **Nothing here deletes data it does not understand.** A write only ever persists entries this
 * build decoded plus the raw JSON of the ones it did not; a blob that is not decodable at all is
 * handed to [LogStorage.quarantine] before the fresh log replaces it. The watch's own storage is
 * the only copy of this data that exists anywhere until a code is scanned (`allowBackup="false"`),
 * so an overwrite here is permanent.
 */
class StandaloneFoodLog(private val storage: LogStorage) {
    companion object {
        const val MAX_ENTRIES = 200
        /** Bound on `StoredLog.unreadable`, matching [MAX_ENTRIES]: the carried-forward shadow list
         *  should never be able to hold more dead weight than the live log itself could ever hold. */
        const val MAX_UNREADABLE_ENTRIES = 200
    }
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
        val skipped = (root["skipped"] as? JsonPrimitive)?.intOrNull ?: 0
        val previouslyCarried = (root["unreadable"] as? JsonArray)?.toList() ?: emptyList()
        val previouslyDropped = (root["unreadableDropped"] as? JsonPrimitive)?.intOrNull ?: 0

        // Retry every carried entry on every load: the shape a past build could not parse may be
        // one this build understands fine (a downgrade-then-upgrade cycle, say). Anything that now
        // decodes rejoins the live log instead of staying inert for the life of the install.
        val stillBad = mutableListOf<JsonElement>()
        previouslyCarried.forEach { element ->
            runCatching { json.decodeFromJsonElement(LoggedFood.serializer(), element) }
                .fold({ good += it }, { stillBad += element })
        }

        // `stillBad` is oldest first (it already survived at least one retry); this pass's own
        // failures are newest. Bounded so a build that can never parse a given shape doesn't grow
        // this list forever — the oldest bytes are the ones dropped, and the drop is counted rather
        // than silent.
        val carried = stillBad + bad
        val (unreadable, newlyDropped) =
            if (carried.size > MAX_UNREADABLE_ENTRIES) carried.takeLast(MAX_UNREADABLE_ENTRIES) to (carried.size - MAX_UNREADABLE_ENTRIES)
            else carried to 0

        return Loaded(StoredLog(good, skipped, unreadable, previouslyDropped + newlyDropped), null)
    }

    /** The log as it stands, with any wholly undecodable blob set aside first — the caller is about
     *  to write, and writing over bytes we did not understand is how a log disappears. */
    private fun loadForWrite(): StoredLog {
        val loaded = load()
        loaded.undecodable?.let { storage.quarantine(it) }
        return loaded.log ?: StoredLog()
    }

    private fun save(log: StoredLog) = storage.write(json.encodeToString(StoredLog.serializer(), log).toByteArray())

    /** Everything storage holds, oldest first. If it is retained, it is exportable. */
    val entries: List<LoggedFood> get() =
        (load().log?.entries ?: emptyList()).sortedBy { it.loggedAtEpochSeconds }
    val skippedCount: Int get() = load().log?.skipped ?: 0
    /** Entries this build still cannot decode, after retrying every one of them on this load. */
    val unreadableCount: Int get() = load().log?.unreadable?.size ?: 0
    /** Unreadable entries dropped for exceeding [MAX_UNREADABLE_ENTRIES] — a count, never silence. */
    val unreadableDroppedCount: Int get() = load().log?.unreadableDropped ?: 0
    /** Corrupt blobs handed to [LogStorage.quarantine] that lost first-wins and were discarded. */
    val quarantineDiscardedCount: Int get() = storage.quarantineDiscardedCount()

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
}
