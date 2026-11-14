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

    @Test fun `a reading older than the last one sent is never sent`() {
        assertFalse(policy.shouldSend(990L, 1_000L))
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
