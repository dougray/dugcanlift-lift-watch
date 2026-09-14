package com.dugcanlift.liftkit
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StandaloneFoodLogTest {
    private val oats = WatchFood("Oats", 379.0, 13.15, 6.52, 67.7, 10.1)
    private val chicken = WatchFood("Chicken", 165.0, 31.02, 3.57, 0.0, 0.0)
    private val chicago: ZoneId = ZoneId.of("America/Chicago")
    private fun at(iso: String) = ZonedDateTime.parse(iso).toEpochSecond()
    private fun entry(food: WatchFood, secs: Long, meal: FoodLogMeal = FoodLogMeal.LUNCH) = LoggedFood(food, 100.0, meal, secs)

    @Test fun `appended entries survive a new instance`() {
        val storage = InMemoryLogStorage()
        StandaloneFoodLog(storage).append(entry(oats, at("2026-09-13T08:00:00-05:00")))
        assertEquals(1, StandaloneFoodLog(storage).entries.size)
    }
    @Test fun `entries come back oldest first`() {
        val log = StandaloneFoodLog(InMemoryLogStorage())
        log.append(entry(oats, at("2026-09-13T09:00:00-05:00"))); log.append(entry(chicken, at("2026-09-13T07:00:00-05:00")))
        assertEquals(listOf(chicken, oats), log.entries.map { it.food })
    }
    @Test fun `entry count is capped dropping oldest first`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val log = StandaloneFoodLog(InMemoryLogStorage())
        repeat(205) { i -> log.append(entry(oats, now - 3600 + i)) }
        assertEquals(200, log.entries.size)
        assertEquals(now - 3600 + 5, log.entries.first().loggedAtEpochSeconds)
    }
    /** Was `entries older than the age cap are dropped`. Settled 2026-09-13: there is no age cap,
     *  on either watch. A 74-day-old entry exports like any other. */
    @Test fun `age alone never removes an entry from the log`() {
        val now = at("2026-09-13T12:00:00-05:00")
        val log = StandaloneFoodLog(InMemoryLogStorage())
        log.append(entry(oats, at("2026-07-01T12:00:00-05:00")))   // 74 days old
        log.append(entry(chicken, now - 60))
        assertEquals(listOf(oats, chicken), log.entries.map { it.food })
    }
    @Test fun `clear empties the log`() {
        val log = StandaloneFoodLog(InMemoryLogStorage())
        log.append(entry(oats, at("2026-09-13T08:00:00-05:00"))); log.clear(); assertTrue(log.entries.isEmpty())
    }
    @Test fun `remove takes one occurrence per request not every equal entry`() {
        val now = at("2026-09-13T12:00:00-05:00"); val log = StandaloneFoodLog(InMemoryLogStorage())
        val e = entry(oats, now - 100); log.append(e); log.append(e)
        log.remove(listOf(e)); assertEquals(1, log.entries.size)
    }
    @Test fun `remove of both duplicates empties them`() {
        val now = at("2026-09-13T12:00:00-05:00"); val log = StandaloneFoodLog(InMemoryLogStorage())
        val e = entry(oats, now - 100); log.append(e); log.append(e)
        log.remove(listOf(e, e)); assertTrue(log.entries.isEmpty())
    }
    @Test fun `remove only deletes the given entries leaving later appends intact`() {
        val now = at("2026-09-13T12:00:00-05:00"); val log = StandaloneFoodLog(InMemoryLogStorage())
        val shown = entry(oats, now - 200); log.append(shown)
        val later = entry(chicken, now - 100); log.append(later)
        log.remove(listOf(shown)); assertEquals(listOf(later), log.entries)
    }
    @Test fun `corrupt stored data reads as empty rather than crashing`() {
        val storage = InMemoryLogStorage().apply { write("not json".toByteArray()) }
        assertTrue(StandaloneFoodLog(storage).entries.isEmpty())
    }
    @Test fun `skipped count starts at zero increments and survives a new instance`() {
        val storage = InMemoryLogStorage()
        val log = StandaloneFoodLog(storage)
        assertEquals(0, log.skippedCount); log.recordSkipped(); log.recordSkipped()
        assertEquals(2, StandaloneFoodLog(storage).skippedCount)
    }
    @Test fun `clear resets skipped count alongside entries`() {
        val log = StandaloneFoodLog(InMemoryLogStorage())
        log.recordSkipped(); log.clear(); assertEquals(0, log.skippedCount)
    }
}
