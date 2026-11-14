package com.dugcanlift.liftwear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The timing rules of a run's heart rate, as values: no clock, no radio, no Health Services. */
class RunHeartRatePolicyTest {
    private val policy = RunHeartRatePolicy()

    @Test fun `the defaults are five seconds between readings and five minutes of silence`() {
        assertEquals(5L, policy.minSecondsBetweenReadings)
        assertEquals(300_000L, policy.silenceLimitMillis)
    }

    @Test fun `the first reading is always sent`() {
        assertTrue(policy.shouldSend(atSeconds = 1_762_800_000L, lastSentAtSeconds = null))
    }

    @Test fun `a reading under five seconds after the last is held back, at five it goes`() {
        val last = 1_000L
        assertFalse(policy.shouldSend(1_000L, last))
        assertFalse(policy.shouldSend(1_004L, last))
        assertTrue(policy.shouldSend(1_005L, last))
        assertTrue(policy.shouldSend(1_060L, last))
    }

    @Test fun `a reading a few seconds older than the last one sent is a late batch and is held`() {
        assertFalse(policy.shouldSend(996L, 1_000L))
        assertFalse(policy.shouldSend(995L, 1_000L))
    }

    @Test fun `a clock set back restarts the allowance instead of silencing the run`() {
        // The wall clock stepped back an hour after a reading went at 1_000.
        assertTrue(policy.shouldSend(1_000L - 3_600L, 1_000L))
        assertTrue(policy.shouldSend(994L, 1_000L))        // just past the late-batch margin
        // And from the new time on, the five seconds apply as they always did.
        assertFalse(policy.shouldSend(1_000L - 3_600L + 4L, 1_000L - 3_600L))
        assertTrue(policy.shouldSend(1_000L - 3_600L + 5L, 1_000L - 3_600L))
    }

    @Test fun `an orphaned run waits five minutes for a start, and not a moment less`() {
        assertEquals(300_000L, policy.orphanGraceMillis)
        assertEquals(300_000L, policy.millisUntilOrphanExpired(nowMillis = 10L, foundAtMillis = 10L))
        assertEquals(1L, policy.millisUntilOrphanExpired(nowMillis = 299_999L, foundAtMillis = 0L))
        assertEquals(0L, policy.millisUntilOrphanExpired(nowMillis = 300_000L, foundAtMillis = 0L))
        assertEquals(0L, policy.millisUntilOrphanExpired(nowMillis = 900_000L, foundAtMillis = 0L))
    }

    @Test fun `silence is judged from the last contact, and not before five minutes`() {
        assertFalse(policy.isSilent(nowMillis = 299_999L, lastContactMillis = 0L))
        assertTrue(policy.isSilent(nowMillis = 300_000L, lastContactMillis = 0L))
        assertFalse(policy.isSilent(nowMillis = 400_000L, lastContactMillis = 150_000L))
        assertTrue(policy.isSilent(nowMillis = 450_000L, lastContactMillis = 150_000L))
    }

    @Test fun `the time until silence counts down and is never negative`() {
        assertEquals(300_000L, policy.millisUntilSilent(nowMillis = 0L, lastContactMillis = 0L))
        assertEquals(100_000L, policy.millisUntilSilent(nowMillis = 200_000L, lastContactMillis = 0L))
        assertEquals(0L, policy.millisUntilSilent(nowMillis = 900_000L, lastContactMillis = 0L))
    }

    @Test fun `a sample is a reading only when it rounds to at least one bpm`() {
        assertNull(RunHeartRatePolicy.wholeBpm(0.0))
        assertNull(RunHeartRatePolicy.wholeBpm(-5.0))
        assertNull(RunHeartRatePolicy.wholeBpm(0.4))
        assertNull(RunHeartRatePolicy.wholeBpm(Double.NaN))
        assertNull(RunHeartRatePolicy.wholeBpm(Double.POSITIVE_INFINITY))
        assertNull(RunHeartRatePolicy.wholeBpm(70_000.0))
        assertEquals(1, RunHeartRatePolicy.wholeBpm(0.5))
        assertEquals(142, RunHeartRatePolicy.wholeBpm(141.5))
        assertEquals(142, RunHeartRatePolicy.wholeBpm(142.0))
    }
}
