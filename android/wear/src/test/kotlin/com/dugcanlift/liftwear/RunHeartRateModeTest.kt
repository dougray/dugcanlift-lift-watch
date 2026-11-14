package com.dugcanlift.liftwear

import com.dugcanlift.liftkit.HeartRatePerSet.Sample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /**
     * One exercise slot, like Health Services' own: starting an exercise **replaces** whatever is in it,
     * and `end()` ends whatever is in it, whoever it belongs to. That is what makes the ownership and
     * ordering rules of the mode testable.
     */
    private class FakeExercise : ExerciseClientLike {
        enum class Slot { NONE, RUN, LIFTING, OTHER_APP }

        override var hasPermission = true
        var slot = Slot.NONE
        var startFails = false
        var starts = 0
        var adoptions = 0
        var ends = 0
        var liftingEnded = 0                 // ends that landed on a lifting exercise
        var statusChecks = 0
        var onReadings: ((List<Sample>) -> Unit)? = null
        var onEnded: (() -> Unit)? = null
        var onRegistrationFailed: (() -> Unit)? = null

        /** When set, `end()` / `startRun()` suspend on it, holding the exercise gate as the real ones do. */
        var endLatch: CompletableDeferred<Unit>? = null
        var startLatch: CompletableDeferred<Unit>? = null

        override suspend fun trackedStatus(): ExerciseClientLike.Tracked {
            statusChecks++
            return when (slot) {
                Slot.NONE -> ExerciseClientLike.Tracked.NONE
                Slot.RUN -> ExerciseClientLike.Tracked.OWNED_RUN
                Slot.LIFTING -> ExerciseClientLike.Tracked.OWNED_OTHER
                Slot.OTHER_APP -> ExerciseClientLike.Tracked.OTHER_APP_IN_PROGRESS
            }
        }

        override suspend fun startRun(onReadings: (List<Sample>) -> Unit, onEnded: () -> Unit, onRegistrationFailed: () -> Unit) {
            if (startFails) throw IllegalStateException("Health Services would not start")
            startLatch?.await()
            starts++
            attach(onReadings, onEnded, onRegistrationFailed)
            slot = Slot.RUN
        }

        override suspend fun adoptRun(onReadings: (List<Sample>) -> Unit, onEnded: () -> Unit, onRegistrationFailed: () -> Unit) {
            adoptions++
            attach(onReadings, onEnded, onRegistrationFailed)
        }

        private fun attach(r: (List<Sample>) -> Unit, e: () -> Unit, f: () -> Unit) {
            onReadings = r; onEnded = e; onRegistrationFailed = f
        }

        override suspend fun end() {
            endLatch?.await()
            ends++
            if (slot == Slot.LIFTING) liftingEnded++
            slot = Slot.NONE
            onReadings = null
        }

        /** What the lifting session's HeartRateRecorder does, behind the same gate. */
        fun startLifting() { slot = Slot.LIFTING }

        /** A sensor reading, timed in epoch seconds. */
        fun sample(bpm: Double, at: Long) = onReadings?.invoke(listOf(Sample(at.toDouble(), bpm)))
    }

    private class Harness(
        val scope: TestScope,
        val exercise: FakeExercise = FakeExercise(),
        lifting: () -> Boolean = { false },
        val gate: Mutex = Mutex(),
    ) {
        val sent = mutableListOf<Pair<Int, Long>>()
        /** The lifting session's HeartRateRecorder: starts its exercise behind the shared gate. */
        fun liftingStarts() = scope.launch { gate.withLock { exercise.startLifting() } }
        val mode = RunHeartRateMode(
            exercise = exercise,
            send = { bpm, at -> sent += bpm to at },
            scope = scope,
            clockMillis = { scope.currentTime },
            liftingActive = lifting,
            clientCalls = gate,
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
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.LIFTING }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        h.mode.stop(); runCurrent()
        assertEquals(0, exercise.starts)
        assertEquals(0, exercise.adoptions)
        assertEquals(0, exercise.ends)
        assertFalse(h.mode.isRunning)
        assertEquals(FakeExercise.Slot.LIFTING, exercise.slot)
    }

    @Test fun `it does nothing while another app has an exercise`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.OTHER_APP }
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
        assertEquals(FakeExercise.Slot.NONE, h.exercise.slot)
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
        h.exercise.slot = FakeExercise.Slot.NONE
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


    // ---- the run never touches lifting, and lifting never lands on the run ---------------------------------

    @Test fun `a run end already in flight finishes before a lifting session starts, so lifting survives`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.endLatch = CompletableDeferred()
        h.mode.stop(); runCurrent()                       // the run's end has begun, and is holding the gate
        h.mode.yieldToLifting()                           // the lifting session starts in that window
        h.liftingStarts(); runCurrent()
        assertEquals(FakeExercise.Slot.RUN, h.exercise.slot)   // lifting has not touched anything yet
        h.exercise.endLatch!!.complete(Unit); runCurrent()
        assertEquals(FakeExercise.Slot.LIFTING, h.exercise.slot)
        assertEquals(0, h.exercise.liftingEnded)
        assertEquals(1, h.exercise.ends)                  // the run's own, once
    }

    @Test fun `a lifting exercise already in the slot is never ended by a run stopping`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.startLifting()                         // replaced behind the mode's back
        h.mode.stop(); runCurrent()
        assertEquals(0, h.exercise.ends)
        assertEquals(0, h.exercise.liftingEnded)
        assertEquals(FakeExercise.Slot.LIFTING, h.exercise.slot)
    }

    @Test fun `the silence timer cannot end a lifting exercise either`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        h.exercise.startLifting()
        advanceTimeBy(301_000); runCurrent()
        assertEquals(0, h.exercise.liftingEnded)
        assertEquals(FakeExercise.Slot.LIFTING, h.exercise.slot)
    }

    @Test fun `a lifting start that gets the gate first means the run never starts over it`() = runTest {
        val h = Harness(this)                             // liftingActive is false: only the status check protects
        h.liftingStarts(); runCurrent()
        h.mode.start(); runCurrent()
        assertEquals(0, h.exercise.starts)
        assertEquals(FakeExercise.Slot.LIFTING, h.exercise.slot)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `a run that is still starting when lifting starts is handed over, not left running over it`() = runTest {
        val h = Harness(this)
        h.exercise.startLatch = CompletableDeferred()
        h.mode.start(); runCurrent()                      // startExercise(RUNNING) is in flight, holding the gate
        h.mode.yieldToLifting()                           // the lifting session starts meanwhile
        h.liftingStarts(); runCurrent()
        h.exercise.startLatch!!.complete(Unit); runCurrent()
        assertEquals(FakeExercise.Slot.LIFTING, h.exercise.slot)   // lifting's start ran after, and replaced it
        assertEquals(0, h.exercise.ends)                  // nothing ended: the run let go
        assertFalse(h.mode.isRunning)
    }

    @Test fun `a lifting session that starts while the run waits to start makes the run give up before starting`() = runTest {
        val h = Harness(this)
        h.mode.start()                                    // queued, nothing has run
        h.mode.yieldToLifting()
        runCurrent()
        assertEquals(0, h.exercise.starts)
        assertEquals(0, h.exercise.ends)
    }

    // ---- a callback that would not register ----------------------------------------------------------------

    @Test fun `a registration failure ends the exercise rather than treating it as already gone`() = runTest {
        val h = Harness(this)
        h.mode.start(); runCurrent()
        assertEquals(FakeExercise.Slot.RUN, h.exercise.slot)
        h.exercise.onRegistrationFailed!!.invoke(); runCurrent()
        assertEquals(1, h.exercise.ends)
        assertEquals(FakeExercise.Slot.NONE, h.exercise.slot)
        assertFalse(h.mode.isRunning)
        advanceTimeBy(10 * 60_000); runCurrent()          // and no timer left to end it twice
        assertEquals(1, h.exercise.ends)
    }

    @Test fun `a registration failure that arrives before the start has returned still ends it`() = runTest {
        val h = Harness(this)
        h.exercise.startLatch = CompletableDeferred()
        h.mode.start(); runCurrent()
        // Health Services reports the failure while startExercise is still returning.
        h.exercise.startLatch!!.complete(Unit)
        h.exercise.onRegistrationFailed?.invoke()
        runCurrent()
        h.exercise.onRegistrationFailed?.invoke(); runCurrent()
        assertEquals(1, h.exercise.ends)
        assertEquals(FakeExercise.Slot.NONE, h.exercise.slot)
    }

    // ---- a run left behind by a killed process -----------------------------------------------------------------

    @Test fun `a start adopts a run left running by a killed process instead of starting another`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        assertEquals(1, exercise.adoptions)
        assertEquals(0, exercise.starts)
        assertTrue(h.mode.isRunning)
        exercise.sample(150.0, t0)
        assertEquals(listOf(150 to t0), h.sent)
        h.mode.stop(); runCurrent()
        assertEquals(1, exercise.ends)
    }

    @Test fun `an adopted run is on the silence timer like any other`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        val h = Harness(this, exercise)
        h.mode.start(); runCurrent()
        advanceTimeBy(301_000); runCurrent()
        assertEquals(1, exercise.ends)
        assertFalse(h.mode.isRunning)
    }

    @Test fun `a lifting exercise is never adopted, with or without a lifting draft`() = runTest {
        listOf(false, true).forEach { draft ->
            val exercise = FakeExercise().apply { slot = FakeExercise.Slot.LIFTING }
            val h = Harness(this, exercise, lifting = { draft })
            h.mode.start(); runCurrent()
            h.mode.stop(); runCurrent()
            h.mode.checkForOrphan(); advanceTimeBy(10 * 60_000); runCurrent()
            assertEquals("draft=$draft", 0, exercise.adoptions)
            assertEquals("draft=$draft", 0, exercise.ends)
            assertEquals("draft=$draft", FakeExercise.Slot.LIFTING, exercise.slot)
        }
    }

    @Test fun `a run is not adopted while a lifting session is on screen`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        val h = Harness(this, exercise, lifting = { true })
        h.mode.start(); runCurrent()
        assertEquals(0, exercise.adoptions)
        assertEquals(0, exercise.ends)
    }

    @Test fun `an orphaned run nobody adopts is ended after the grace period, and not before`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        val h = Harness(this, exercise)
        h.mode.checkForOrphan(); runCurrent()
        advanceTimeBy(299_000); runCurrent()
        assertEquals(0, exercise.ends)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, exercise.ends)
        assertEquals(FakeExercise.Slot.NONE, exercise.slot)
    }

    @Test fun `an orphaned run that a start adopts is not also ended by the orphan timer`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        val h = Harness(this, exercise)
        h.mode.checkForOrphan(); runCurrent()
        advanceTimeBy(100_000)
        h.mode.start(); runCurrent()
        advanceTimeBy(250_000); runCurrent()              // past the orphan grace, inside the adopted run's silence limit
        assertEquals(0, exercise.ends)
        assertTrue(h.mode.isRunning)
        h.mode.stop(); runCurrent()
        assertEquals(1, exercise.ends)
    }

    @Test fun `an orphan check with nothing running, or without permission, does nothing`() = runTest {
        val none = FakeExercise()
        Harness(this, none).mode.checkForOrphan()
        val denied = FakeExercise().apply { slot = FakeExercise.Slot.RUN; hasPermission = false }
        Harness(this, denied).mode.checkForOrphan()
        runCurrent(); advanceTimeBy(10 * 60_000); runCurrent()
        assertEquals(0, none.ends)
        assertEquals(0, denied.ends)
        assertEquals(0, denied.statusChecks)
    }

    @Test fun `an orphan is left alone if a lifting session comes up before the grace runs out`() = runTest {
        val exercise = FakeExercise().apply { slot = FakeExercise.Slot.RUN }
        var lifting = false
        val h = Harness(this, exercise, lifting = { lifting })
        h.mode.checkForOrphan(); runCurrent()
        lifting = true
        advanceTimeBy(6 * 60_000); runCurrent()
        assertEquals(0, exercise.ends)
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
