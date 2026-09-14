package com.dugcanlift.liftkit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The two ways the log used to destroy a user's food silently. Both write through `append`, which
 * is the only path that persists a filtered list: whatever `load()` could not reconstruct, or
 * whatever the device clock disagreed with, was simply not written back.
 */
class StandaloneFoodLogLossTest {
    private val chicago: ZoneId = ZoneId.of("America/Chicago")
    private fun at(iso: String) = ZonedDateTime.parse(iso).toEpochSecond()
    private fun food(name: String) = WatchFood(name, 165.0, 31.02, 3.57, 0.0, 0.0)
    private fun entry(name: String, secs: Long) = LoggedFood(food(name), 100.0, FoodLogMeal.LUNCH, secs)

    /** Everything the storage still holds: the main slot plus anything set aside from it. */
    private fun surviving(storage: InMemoryLogStorage) =
        String(storage.read() ?: ByteArray(0)) + String(storage.quarantined ?: ByteArray(0))

    /** The shape a future non-defaulted field in `LoggedFood` produces on an older build: the JSON
     *  is well formed, but every entry object is missing a key the decoder requires. */
    private fun dropKeyFromEveryEntry(bytes: ByteArray, key: String): ByteArray {
        val root = Json.parseToJsonElement(String(bytes)).jsonObject
        val stripped = root.getValue("entries").jsonArray.map { JsonObject(it.jsonObject.filterKeys { k -> k != key }) }
        return Json.encodeToString(JsonObject.serializer(), JsonObject(root + ("entries" to JsonArray(stripped)))).toByteArray()
    }

    @Test fun `an append does not destroy entries this build could not decode`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val storage = InMemoryLogStorage()
        val names = listOf("Oats", "Chicken", "Rice", "Black beans", "Kale")
        val log = StandaloneFoodLog(storage)
        names.forEachIndexed { i, n -> log.append(entry(n, now - 500 + i)) }

        storage.write(dropKeyFromEveryEntry(storage.read()!!, "grams"))
        val reopened = StandaloneFoodLog(storage)
        assertEquals("undecodable entries must not read as live entries", 0, reopened.entries.size)

        reopened.append(entry("Lentils", now - 10))

        val left = surviving(storage)
        names.forEach { assertTrue("lost `$it` - the append overwrote data it could not read", left.contains(it)) }
        assertTrue("the new entry was not stored", left.contains("Lentils"))
    }

    @Test fun `a blob that is not JSON at all is set aside before a fresh log is written`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val storage = InMemoryLogStorage().apply { write("<<not json at all>>".toByteArray()) }
        val log = StandaloneFoodLog(storage)
        log.append(entry("Oats", now - 10))
        assertEquals("the undecodable bytes were destroyed", "<<not json at all>>", String(storage.quarantined ?: ByteArray(0)))
        assertEquals(1, log.entries.size)
    }

    @Test fun `a clock correction after a flat battery does not delete what was logged before it`() {
        // The watch boots with a stale RTC, the user logs breakfast and lunch, the clock then syncs
        // to the real date, and the user logs dinner. Breakfast and lunch were logged minutes ago.
        val storage = InMemoryLogStorage()
        var now = at("2016-01-02T08:00:00-06:00")
        val log = StandaloneFoodLog(storage)
        log.append(entry("Oats", now))
        log.append(entry("Chicken", now + 3600))
        assertEquals(2, log.entries.size)

        now = at("2026-09-13T12:00:00-05:00")
        log.append(entry("Lentils", now))

        val left = surviving(storage)
        listOf("Oats", "Chicken", "Lentils").forEach {
            assertTrue("lost `$it` - retention trimming acted on a clock correction", left.contains(it))
        }
        // Settled 2026-09-13: retained *and* exportable. Nothing is hidden by age.
        assertEquals("the stale-clock entries are exported like any other", 3, log.entries.size)
    }

    @Test fun `an entry stamped in the future is not treated as expired`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val log = StandaloneFoodLog(InMemoryLogStorage())
        log.append(entry("Oats", now + 365L * 86_400))
        assertEquals(1, log.entries.size)
    }

    /** Was `a clock behind the stored log suspends the age window entirely`. There is no window to
     *  suspend now, and no clock read anywhere in the store — the guarantee the old test bought
     *  with two clock guards is now structural. */
    @Test fun `no reading of the clock can change what the log returns`() {
        val storage = InMemoryLogStorage()
        val log = StandaloneFoodLog(storage)
        log.append(entry("Oats", at("2026-01-01T12:00:00-06:00")))
        log.append(entry("Chicken", at("2026-09-13T12:00:00-05:00")))
        assertEquals(2, log.entries.size)
        assertEquals("a reopened log sees the same entries", 2, StandaloneFoodLog(storage).entries.size)
    }
}
