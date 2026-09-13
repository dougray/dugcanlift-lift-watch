package com.dugcanlift.liftkit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 4: `unreadable` carried entries were never retried and never bounded, and a second
 * wholly-undecodable blob handed to `quarantine` vanished with no trace.
 */
class StandaloneFoodLogRecoveryTest {
    private val chicago: ZoneId = ZoneId.of("America/Chicago")
    private fun at(iso: String) = ZonedDateTime.parse(iso).toEpochSecond()
    private fun food(name: String) = WatchFood(name, 165.0, 31.02, 3.57, 0.0, 0.0)
    private fun entry(name: String, secs: Long) = LoggedFood(food(name), 100.0, FoodLogMeal.LUNCH, secs)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun storedBlob(entries: JsonArray = JsonArray(emptyList()), unreadable: JsonArray = JsonArray(emptyList()), skipped: Int = 0) =
        JsonObject(mapOf("entries" to entries, "skipped" to JsonPrimitive(skipped), "unreadable" to unreadable))

    @Test fun `an entry that failed under an old shape decodes once that shape is understood again`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val storage = InMemoryLogStorage()
        val recovered = json.encodeToJsonElement(LoggedFood.serializer(), entry("Oats", now - 100))
        // Missing every key but `food`: the shape a non-defaulted field addition leaves behind.
        val stillBad = JsonObject(mapOf("food" to json.encodeToJsonElement(WatchFood.serializer(), food("Mystery"))))
        storage.write(json.encodeToString(JsonObject.serializer(),
            storedBlob(unreadable = JsonArray(listOf(recovered, stillBad)))).toByteArray())

        val log = StandaloneFoodLog(storage, { now }, chicago)
        assertEquals("a now-decodable carried entry must rejoin the live log",
            listOf("Oats"), log.entries.map { it.food.name })
        assertEquals("the still-undecodable entry must remain carried, not counted as live",
            1, log.unreadableCount)

        // A write must persist the recovery: the fixed entry does not fall back into `unreadable`.
        log.append(entry("Kale", now))
        assertEquals(setOf("Oats", "Kale"), log.entries.map { it.food.name }.toSet())
        assertEquals(1, log.unreadableCount)
    }

    @Test fun `unreadable entries beyond the cap are dropped oldest first and counted, not silently lost`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val storage = InMemoryLogStorage()
        val overflow = 7
        val junk = (0 until StandaloneFoodLog.MAX_UNREADABLE_ENTRIES + overflow)
            .map { JsonObject(mapOf("marker" to JsonPrimitive(it))) }
        storage.write(json.encodeToString(JsonObject.serializer(),
            storedBlob(unreadable = JsonArray(junk))).toByteArray())

        val log = StandaloneFoodLog(storage, { now }, chicago)
        assertEquals(StandaloneFoodLog.MAX_UNREADABLE_ENTRIES, log.unreadableCount)
        assertEquals("the overflow must be counted rather than silently discarded", overflow, log.unreadableDroppedCount)

        log.append(entry("Kale", now))   // a write is where the cap is actually persisted
        val persisted = json.parseToJsonElement(String(storage.read()!!)).jsonObject
        val remainingMarkers = persisted.getValue("unreadable").jsonArray.map { it.jsonObject.getValue("marker").jsonPrimitive.int }
        assertEquals("the oldest unreadable bytes should be the ones dropped",
            (overflow until StandaloneFoodLog.MAX_UNREADABLE_ENTRIES + overflow).toList(), remainingMarkers)
        assertEquals(overflow, persisted.getValue("unreadableDropped").jsonPrimitive.int)
    }

    @Test fun `a second corruption increments a discard counter in LogStorage instead of vanishing`() {
        val storage = InMemoryLogStorage()
        storage.quarantine("first corruption".toByteArray())
        storage.quarantine("second corruption".toByteArray())
        assertEquals("first-wins must still hold the first blob",
            "first corruption", String(storage.quarantined ?: ByteArray(0)))
        assertEquals("the second, discarded corruption must be counted, not silently lost",
            1, storage.quarantineDiscardedCount())
        storage.quarantine("third corruption".toByteArray())
        assertEquals(2, storage.quarantineDiscardedCount())
    }

    @Test fun `two undecodable blobs across two writes both surface via the log's own count`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val storage = InMemoryLogStorage().apply { write("<<not json at all>>".toByteArray()) }
        val log = StandaloneFoodLog(storage, { now }, chicago)
        log.append(entry("Oats", now))   // quarantines the first corrupt blob
        assertEquals(0, log.quarantineDiscardedCount)

        // A second, unrelated corruption of the main slot must not vanish either.
        storage.write("<<also not json>>".toByteArray())
        log.append(entry("Kale", now))
        assertEquals("a second corruption must be counted, not silently lost", 1, log.quarantineDiscardedCount)
    }
}
