package com.dugcanlift.liftkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rest timer's arithmetic. Every case passes an explicit `now`, because a timer that reads the
 * clock itself is a timer nobody can test — which is the whole reason this is a value type and not a
 * coroutine in a composable.
 */
class RestTimerTest {
    private val start = 1_758_800_000_000L

    @Test fun `a timer that has not started has no remaining time and has not finished`() {
        val timer = RestTimer(intervalSeconds = 90)
        assertNull(timer.remainingSeconds(start))
        assertFalse(timer.isRunning)
        assertFalse(timer.hasFinished(start))
        assertEquals(0.0, timer.progress(start), 1e-9)
    }

    @Test fun `remaining time is measured from the start instant, not from a tick`() {
        val timer = RestTimer(intervalSeconds = 120).started(start)
        assertEquals(120.0, timer.remainingSeconds(start)!!, 1e-9)
        assertEquals(90.0, timer.remainingSeconds(start + 30_000)!!, 1e-9)
        // The app was suspended for a minute and a half and the answer is still right.
        assertEquals(30.0, timer.remainingSeconds(start + 90_000)!!, 1e-9)
    }

    @Test fun `remaining time floors at zero rather than going negative`() {
        val timer = RestTimer(intervalSeconds = 60).started(start)
        assertEquals(0.0, timer.remainingSeconds(start + 600_000)!!, 1e-9)
        assertTrue(timer.hasFinished(start + 600_000))
    }

    @Test fun `it has finished exactly at zero, which is when the haptic fires`() {
        val timer = RestTimer(intervalSeconds = 90).started(start)
        assertFalse(timer.hasFinished(start + 89_999))
        assertTrue(timer.hasFinished(start + 90_000))
    }

    @Test fun `progress runs nought to one and is clamped at both ends`() {
        val timer = RestTimer(intervalSeconds = 100).started(start)
        assertEquals(0.0, timer.progress(start), 1e-9)
        assertEquals(0.25, timer.progress(start + 25_000), 1e-9)
        assertEquals(1.0, timer.progress(start + 100_000), 1e-9)
        assertEquals(1.0, timer.progress(start + 500_000), 1e-9)
    }

    @Test fun `a zero interval never reports progress rather than dividing by it`() {
        assertEquals(0.0, RestTimer(intervalSeconds = 0).started(start).progress(start + 1000), 1e-9)
    }

    @Test fun `stopping clears the start instant and leaves the interval alone`() {
        val timer = RestTimer(intervalSeconds = 75).started(start).stopped()
        assertFalse(timer.isRunning)
        assertEquals(75, timer.intervalSeconds)
    }

    @Test fun `a prescription with no rest gets the default, never zero seconds`() {
        val timer = RestTimer()
        assertEquals(RestTimer.DEFAULT_SECONDS, timer.forInterval(null).intervalSeconds)
        assertEquals(RestTimer.DEFAULT_SECONDS, timer.forInterval(0).intervalSeconds)
        assertEquals(RestTimer.DEFAULT_SECONDS, timer.forInterval(-5).intervalSeconds)
        assertEquals(180, timer.forInterval(180).intervalSeconds)
    }

    @Test fun `a fresh interval is not running until it is started`() {
        assertFalse(RestTimer().forInterval(120).isRunning)
    }

    @Test fun `the clock reads minutes and seconds, rounded not truncated`() {
        assertEquals("00:00", RestTimer.format(0.0))
        assertEquals("01:37", RestTimer.format(97.0))
        assertEquals("01:37", RestTimer.format(96.6))
        assertEquals("01:30", RestTimer.format(90.0))
        assertEquals("03:00", RestTimer.format(180.0))
        assertEquals("00:00", RestTimer.format(-12.0))
        assertEquals("10:05", RestTimer.format(605.0))
    }
}
