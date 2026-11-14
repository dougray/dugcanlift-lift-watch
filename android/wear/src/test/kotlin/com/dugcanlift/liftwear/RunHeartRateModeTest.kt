package com.dugcanlift.liftwear

import com.dugcanlift.liftkit.HeartRatePerSet.Sample
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heart rate during a run, against a fake exercise client and a virtual clock. Nothing here is a
 * watch: what these prove is the mode's own rules — what it sends, when it refuses, and that it
 * always ends the exercise it started and never one it did not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunHeartRateModeTest {
    private class FakeExercise : ExerciseClientLike {
        override var hasPermission = true
        var status = ExerciseClientLike.Tracked.NONE
        var startFails = false
        var starts = 0
        var ends = 0
        var statusChecks = 0
        var onReadings: ((List<Sample>) -> Unit)? = null
        var onEnded: (() -> Unit)? = null

        override suspend fun trackedStatus(): ExerciseClientLike.Tracked { statusChecks++; return status }
        override suspend fun startRun(onReadings: (List<Sample>) -> Unit, onEnded: () -> Unit) {
            if (startFails) throw IllegalStateException("Health Services would not start")
            starts++
            this.onReadings = onReadings
            this.onEnded = onEnded
            status = ExerciseClientLike.Tracked.OWNED_IN_PROGRESS
        }
        override suspend fun end() {
            ends++
            status = ExerciseClientLike.Tracked.NONE
            onReadings = null
        }
        /** A sensor reading, timed in epoch seconds. */
        fun sample(bpm: Double, at: Long) = onReadings?.invoke(listOf(Sample(at.toDouble(), bpm)))
    }

    private class Harness(scope: TestScope, val exercise: FakeExercise = FakeExercise(), lifting: () -> Boolean = { false }) {
        val sent = mutableListOf<Pair<Int, Long>>()
        val mode = RunHeartRateMode(
            exercise = exercise,
            send = { bpm, at -> sent += bpm to at },
            scope = scope,
            clockMillis = { scope.currentTime },
            liftingActive = lifting,
        )
    }

    private val t0 = 1_762_800_000L

    // ---- what it sends ----------------------------------------------------------------------------

    @Test fun `it starts one exercise and sends what the sensor reads`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        assertEquals(1, h.exercise.starts)
        assertTrue(h.mode.isRunning)
        h.exercise.sample(142.0, t0)
        assertEquals(listOf(142 to t0), h.sent)
        h.mode.stop(); runCurrent()
    }

    @Test fun `it sends at most one reading every five seconds`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        (0L..12L).forEach { s -> h.exercise.sample(140.0 + s, t0 + s) }     // one a second for 13 s
        assertEquals(listOf(140 to t0, 145 to t0 + 5, 150 to t0 + 10), h.sent)
        h.mode.stop(); runCurrent()
    }

    @Test fun `a batch sends only its newest reading, not every one in it`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.onReadings!!.invoke((0L..20L).map { Sample((t0 + it).toDouble(), 100.0 + it) })
        assertEquals(listOf(120 to t0 + 20), h.sent)
        h.mode.stop(); runCurrent()
    }

    @Test fun `a bpm of zero or less is no reading and is never sent`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.sample(0.0, t0)
        h.exercise.sample(-1.0, t0 + 10)
        h.exercise.sample(Double.NaN, t0 + 20)
        h.exercise.sample(0.3, t0 + 30)                       // rounds to nothing
        assertTrue(h.sent.isEmpty())
        // And a zero does not use up the five-second allowance of the reading after it.
        h.exercise.sample(120.0, t0 + 31)
        assertEquals(listOf(120 to t0 + 31), h.sent)
        h.mode.stop(); runCurrent()
    }

    @Test fun `a zero inside a batch is skipped and the newest real reading is sent`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.onReadings!!.invoke(listOf(Sample(t0.toDouble(), 130.0), Sample((t0 + 3).toDouble(), 0.0)))
        assertEquals(listOf(130 to t0), h.sent)
        h.mode.stop(); runCurrent()
    }

    @Test fun `nothing is sent after stop, even by a late callback`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        val late = h.exercise.onReadings!!
        h.mode.stop()
        late(listOf(Sample(t0.toDouble(), 150.0)))
        runCurrent()
        assertTrue(h.sent.isEmpty())
    }

    @Test fun `a send that throws does not end the run`() = runTest {
        val exercise = FakeExercise()
        var calls = 0
        val mode = RunHeartRateMode(exercise, send = { _, _ -> calls++; error("radio") }, scope = this, clockMillis = { currentTime })
        mode.start(); runCurrent()
        exercise.sample(140.0, t0)
        exercise.sample(141.0, t0 + 5)
        assertEquals(2, calls)
        assertTrue(mode.isRunning)
        mode.stop(); runCurrent()
    }

    // ---- when it refuses --------------------------------------------------------------------------

    @Test fun `it does nothing while a lifting session is running`() = runTest {
        val h = Harness(this, lifting = { true })
        h.mode.start(); runCurrent()
        assertEquals(0, h.exercise.starts)
        assertEquals(0, h.exercise.ends)
        assertEquals(0, h.exercise.statusChecks)
        assertFalse(h.mode.isRunning)
        assertTrue(h.sent.isEmpty())
    }

    @Test fun `it does nothing while the watch's own lifting exercise is active, and never ends it`() = runTest {
        val exercise = FakeExercise().apply { status = ExerciseClientLike.Tracked.OWNED_IN_PROGRESS }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        h.mode.stop(); runCurrent()
        assertEquals(0, exercise.starts)
        assertEquals(0, exercise.ends)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `it does nothing while another app has an exercise`() = runTest {
        val exercise = FakeExercise().apply { status = ExerciseClientLike.Tracked.OTHER_APP_IN_PROGRESS }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        h.mode.stop(); runCurrent()
        assertEquals(0, exercise.starts)
        assertEquals(0, exercise.ends)
    }

    @Test fun `without the permission it does nothing and does not crash`() = runTest {
        val exercise = FakeExercise().apply { hasPermission = false }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        h.mode.stop(); runCurrent()
        assertEquals(0, exercise.starts)
        assertEquals(0, exercise.ends)
        assertEquals(0, exercise.statusChecks)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `an exercise Health Services will not start leaves nothing running and nothing to end`() = runTest {
        val exercise = FakeExercise().apply { startFails = true }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        assertFalse(h.mode.isRunning)
        assertEquals(0, exercise.ends)
        // A later try, once Health Services can, works: it did not stay wedged.
        exercise.startFails = false
        h.mode.start(); runCurrent()
        assertTrue(h.mode.isRunning)
        h.mode.stop(); runCurrent()
    }

    // ---- one exercise, always ended ---------------------------------------------------------------

    @Test fun `a second start while running is a no-op`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.mode.start(); runCurrent()
        h.mode.start(); runCurrent()
        assertEquals(1, h.exercise.starts)
        h.mode.stop(); runCurrent()
        assertEquals(1, h.exercise.ends)
    }

    @Test fun `a duplicate start arriving before the first has finished starting is a no-op`() = runTest {
        val h = Harness(this)
        h.mode.start()
        h.mode.start()                       // nothing has run yet: the first is still starting
        runCurrent()
        assertEquals(1, h.exercise.starts)
        h.mode.stop(); runCurrent()
    }

    @Test fun `stop is idempotent`() = runTest {
        val h = Harness(this)
        h.mode.stop(); runCurrent()                      // before anything
        assertEquals(0, h.exercise.ends)
        h.mode.start(); runCurrent()
        h.mode.stop(); h.mode.stop(); h.mode.stop(); runCurrent()
        assertEquals(1, h.exercise.ends)
        h.mode.stop(); runCurrent()
        assertEquals(1, h.exercise.ends)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `a stop that arrives while the exercise is still starting ends it`() = runTest {
        val h = Harness(this)
        h.mode.start()
        h.mode.stop()                        // before the coroutine has run at all
        runCurrent()
        assertEquals(h.exercise.starts, h.exercise.ends)       // whatever was started was ended
        assertFalse(h.mode.isRunning)
        assertEquals(ExerciseClientLike.Tracked.NONE, h.exercise.status)
    }

    @Test fun `it can run again after it has stopped`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent(); h.mode.stop(); runCurrent()
        h.mode.start(); runCurrent()
        assertEquals(2, h.exercise.starts)
        h.exercise.sample(130.0, t0)
        assertEquals(listOf(130 to t0), h.sent)
        h.mode.stop(); runCurrent()
        assertEquals(2, h.exercise.ends)
    }

    @Test fun `link loss ends the exercise`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.mode.linkLost(); runCurrent()
        assertEquals(1, h.exercise.ends)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `an exercise something else ended is not ended again`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.status = ExerciseClientLike.Tracked.NONE
        h.exercise.onEnded!!.invoke()
        runCurrent()
        assertFalse(h.mode.isRunning)
        h.mode.stop(); runCurrent()
        assertEquals(0, h.exercise.ends)
    }

    @Test fun `when a lifting session takes over, the run steps aside without touching the exercise`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.mode.yieldToLifting(); runCurrent()
        assertFalse(h.mode.isRunning)
        assertEquals(0, h.exercise.ends)       // lifting's own startExercise replaces ours; ending would end *its*
        h.exercise.sample(150.0, t0)
        assertTrue(h.sent.isEmpty())
        h.mode.stop(); runCurrent()
        assertEquals(0, h.exercise.ends)
    }

    // ---- the phone that vanished ------------------------------------------------------------------

    @Test fun `five minutes with nothing from the phone ends the exercise`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        // Virtual time, and `runCurrent` rather than `advanceUntilIdle` all through this file: idling
        // would skip five minutes ahead and let the silence timer end the run the test is checking.
        advanceTimeBy(299_000)
        assertTrue(h.mode.isRunning)
        assertEquals(0, h.exercise.ends)
        advanceTimeBy(2_000); runCurrent()
        assertFalse(h.mode.isRunning)
        assertEquals(1, h.exercise.ends)
    }

    @Test fun `a refresh from the phone keeps it going, and the five minutes count from the last one`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        advanceTimeBy(200_000)
        h.mode.start()                                    // the refresh
        advanceTimeBy(200_000)                            // 400 s since the first, 200 s since the refresh
        assertTrue(h.mode.isRunning)
        assertEquals(1, h.exercise.starts)
        advanceTimeBy(101_000); runCurrent()        // now 301 s since the refresh
        assertFalse(h.mode.isRunning)
        assertEquals(1, h.exercise.ends)
    }

    @Test fun `any word from the phone counts as contact`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        advanceTimeBy(250_000)
        h.mode.contact()
        advanceTimeBy(250_000)
        assertTrue(h.mode.isRunning)
        advanceTimeBy(60_000); runCurrent()
        assertFalse(h.mode.isRunning)
    }

    @Test fun `contact when nothing is running starts nothing`() = runTest {
        val h = Harness(this)
        h.mode.contact(); runCurrent()
        assertEquals(0, h.exercise.starts)
        assertEquals(0, h.exercise.statusChecks)
    }

    @Test fun `once stopped there is no timer left to end anything later`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.mode.stop(); runCurrent()
        advanceTimeBy(10 * 60_000); runCurrent()
        assertEquals(1, h.exercise.ends)
    }
}
