package com.dugcanlift.liftwear

import androidx.test.core.app.ApplicationProvider
import com.dugcanlift.liftkit.RestTimer
import com.dugcanlift.liftkit.WeightUnit
import com.dugcanlift.liftkit.headline
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The session the watch is training, end to end, with a real [SessionStore] on Robolectric's
 * SharedPreferences — so "it comes back after the process is killed" is actually exercised rather
 * than asserted about a value in memory.
 *
 * The radio is a real [PhoneLinkPeripheral] with nothing connected, which is the state a watch spends
 * most of its life in: every send returns early, and nothing about logging a set depends on it.
 */
@RunWith(RobolectricTestRunner::class)
class SessionControllerTest {
    /** The local day the fake clock sits on, so a plan can be "today". */
    private val nowMillis = 1_758_800_000_000L
    private val today = com.dugcanlift.liftkit.dayKey(nowMillis / 1000)

    private lateinit var store: SessionStore
    private lateinit var link: PhoneLinkPeripheral
    private var ids = 0

    private fun controller() = SessionController(
        store = store,
        link = link,
        heart = null,
        clockMillis = { nowMillis },
        idFactory = { "id-${ids++}" },
    )

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("liftwear_session", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SessionStore(context)
        link = PhoneLinkPeripheral(context, PhoneLinkStore(context))
        ids = 0
    }

    private fun push(plan: Plan) = store.receive(plan, nowMillis / 1000)

    private fun pushPlan(vararg exercises: PlanExercise) =
        push(Plan("plan-1", 1, "Push", PlanSource.ROUTINE, today, exercises.toList()))

    // ---- a plan reaching the watch ----------------------------------------------------------------

    @Test fun `a pushed plan is today's plan, and yesterday's is not`() {
        pushPlan(PlanExercise("Bench Press", sets = List(4) { PrescribedSet(reps = 6) }))
        assertEquals("Push", controller().todaysPlan()?.name)

        push(Plan("plan-2", 1, "Old", PlanSource.ROUTINE, "2020-01-01", emptyList()))
        assertNull(controller().todaysPlan())
    }

    @Test fun `a plan survives the app being killed`() {
        pushPlan(PlanExercise("Bench Press", sets = List(4) { PrescribedSet(reps = 6) }))
        // A whole new store over the same preferences is what a fresh process sees.
        val reopened = SessionStore(ApplicationProvider.getApplicationContext())
        assertEquals("Push", reopened.plan?.plan()?.name)
    }

    // ---- Doug's routine: four sets of six reps, no weight -------------------------------------------

    @Test fun `Doug's routine starts, reads six reps and no weight, and logs four sets`() {
        pushPlan(PlanExercise("Bench Press", equipment = "barbell", sets = List(4) { PrescribedSet(reps = 6) }))
        val session = controller()
        assertTrue(session.startPlanned())

        val exerciseId = session.guidedExerciseId()!!
        assertEquals("1/4", session.guided.value!!.positionText)
        assertEquals("6 reps", session.guided.value!!.currentPrescription!!.headline(WeightUnit.POUNDS))
        assertNull(session.suggestedSide(exerciseId))

        repeat(4) { session.logSet(exerciseId, weightKg = 0.0, reps = 6, rpe = null, side = null) }
        assertTrue(session.guided.value!!.isComplete)
        assertEquals(4, session.draft.value!!.completedSetCount)
        // Weightless in, weightless out: the volume is zero because nothing was weighed, and the
        // wire carries no weight at all rather than a lift of nothing.
        assertEquals(0.0, session.draft.value!!.totalVolumeKg, 1e-9)
        assertNull(session.draft.value!!.exercises.single().sets.first().weightKg.takeIf { it > 0.0 })
    }

    // ---- rest ---------------------------------------------------------------------------------------

    @Test fun `rest starts by itself from the seconds the set just performed prescribed`() {
        pushPlan(
            PlanExercise(
                "Bench Press",
                sets = listOf(
                    PrescribedSet(reps = 5, restSeconds = 180),
                    PrescribedSet(reps = 5, restSeconds = 60),
                ),
            )
        )
        val session = controller()
        session.startPlanned()
        val id = session.guidedExerciseId()!!

        session.logSet(id, 40.0, 5, null, null)
        assertEquals(180, session.rest.value.intervalSeconds)
        assertTrue(session.rest.value.isRunning)

        session.logSet(id, 40.0, 5, null, null)
        assertEquals(60, session.rest.value.intervalSeconds)
    }

    @Test fun `a prescription with no rest seconds gets the default, never zero`() {
        pushPlan(PlanExercise("Bench Press", sets = List(2) { PrescribedSet(reps = 6) }))
        val session = controller()
        session.startPlanned()
        session.logSet(session.guidedExerciseId()!!, 0.0, 6, null, null)
        assertEquals(RestTimer.DEFAULT_SECONDS, session.rest.value.intervalSeconds)
    }

    @Test fun `skipping rest stops it and leaves the workout alone`() {
        pushPlan(PlanExercise("Bench Press", sets = List(2) { PrescribedSet(reps = 6) }))
        val session = controller()
        session.startPlanned()
        session.logSet(session.guidedExerciseId()!!, 0.0, 6, null, null)
        session.skipRest()
        assertEquals(false, session.rest.value.isRunning)
        assertEquals(1, session.draft.value!!.completedSetCount)
    }

    // ---- the next exercise follows on ----------------------------------------------------------------

    @Test fun `the next exercise follows on when this one's sets are done`() {
        pushPlan(
            PlanExercise("Bench Press", sets = List(2) { PrescribedSet(reps = 6) }),
            PlanExercise("Lat Pulldown", equipment = "cable", sets = List(3) { PrescribedSet(reps = 10) }),
        )
        val session = controller()
        session.startPlanned()
        val bench = session.guidedExerciseId()!!
        repeat(2) { session.logSet(bench, 40.0, 6, null, null) }

        val pulldown = session.guidedExerciseId()!!
        assertEquals("Lat Pulldown", session.draft.value!!.exercise(pulldown)!!.name)
        assertEquals("1/3", session.guided.value!!.positionText)
    }

    @Test fun `opening an exercise out of order moves the guided session with it`() {
        pushPlan(
            PlanExercise("Bench Press", sets = List(2) { PrescribedSet(reps = 6) }),
            PlanExercise("Lat Pulldown", sets = List(2) { PrescribedSet(weightKg = 50.0, reps = 10) }),
        )
        val session = controller()
        session.startPlanned()
        val second = session.draft.value!!.exercises[1].id
        session.focus(second)
        assertEquals(second, session.guidedExerciseId())
        assertEquals(50.0, session.prescription(second)!!.weightKg!!, 1e-9)
    }

    // ---- per side -------------------------------------------------------------------------------------

    @Test fun `an each-side exercise counts per side and offers the sides in turn`() {
        pushPlan(PlanExercise("Split Squat", sets = List(3) { PrescribedSet(weightKg = 20.0, reps = 8) }, eachSide = true))
        val session = controller()
        session.startPlanned()
        val id = session.guidedExerciseId()!!

        assertEquals("1/6 · L", session.guided.value!!.positionText)
        assertEquals(LogSide.LEFT, session.suggestedSide(id))

        session.logSet(id, 20.0, 8, null, LogSide.LEFT)
        assertEquals("2/6 · R", session.guided.value!!.positionText)
        assertEquals(LogSide.RIGHT, session.suggestedSide(id))

        session.logSet(id, 20.0, 8, null, LogSide.RIGHT)
        assertEquals("3/6 · L", session.guided.value!!.positionText)
    }

    @Test fun `the side the lifter actually chose is what gets logged and counted`() {
        pushPlan(PlanExercise("Split Squat", sets = List(2) { PrescribedSet(reps = 8) }, eachSide = true))
        val session = controller()
        session.startPlanned()
        val id = session.guidedExerciseId()!!
        // Offered left, but they did the right first. The log records what happened.
        session.logSet(id, 0.0, 8, null, LogSide.RIGHT)
        assertEquals(LogSide.RIGHT, session.draft.value!!.exercise(id)!!.sets.single().side)
        assertEquals(LogSide.LEFT, session.suggestedSide(id))
    }

    // ---- surviving a process death ---------------------------------------------------------------------

    @Test fun `a session interrupted mid-set comes back exactly where it was`() {
        pushPlan(PlanExercise("Split Squat", sets = List(3) { PrescribedSet(reps = 8, restSeconds = 45) }, eachSide = true))
        val first = controller()
        first.startPlanned()
        val id = first.guidedExerciseId()!!
        first.logSet(id, 0.0, 8, null, LogSide.LEFT)
        first.logSet(id, 0.0, 8, null, LogSide.RIGHT)

        // A new controller over the same store is what the next process sees.
        val resumed = SessionController(
            SessionStore(ApplicationProvider.getApplicationContext()), link, null,
            clockMillis = { nowMillis }, idFactory = { "id-x" },
        )
        assertEquals("3/6 · L", resumed.guided.value!!.positionText)
        assertEquals(2, resumed.draft.value!!.completedSetCount)
        assertEquals(45, resumed.rest.value.intervalSeconds)
        assertTrue(resumed.rest.value.isRunning)
        assertEquals("Split Squat", resumed.draft.value!!.exercises.single().name)
    }

    // ---- free entry -------------------------------------------------------------------------------------

    @Test fun `a workout with no plan logs sets with nothing guiding it`() {
        val session = controller()
        session.startFreeWorkout("Evening")
        assertNull(session.guided.value)
        session.addExercise("Bench Press", "barbell", "bench-barbell")
        val id = session.draft.value!!.exercises.single().id
        session.logSet(id, 40.0, 8, 8.0, null)
        assertEquals(1, session.draft.value!!.completedSetCount)
        assertNull(session.prescription(id))
        assertNull(session.suggestedSide(id))
        assertEquals(RestTimer.DEFAULT_SECONDS, session.rest.value.intervalSeconds)
    }

    @Test fun `the last set of an exercise can be taken back off, for a mis-tap`() {
        val session = controller()
        session.startFreeWorkout()
        session.addExercise("Squat", "barbell", "squat-barbell")
        val id = session.draft.value!!.exercises.single().id
        session.logSet(id, 100.0, 5, null, null)
        session.logSet(id, 100.0, 5, null, null)
        session.removeLastSet(id)
        assertEquals(1, session.draft.value!!.completedSetCount)
    }

    // ---- finishing, and what the phone has not acknowledged -----------------------------------------------

    @Test fun `finishing puts the whole session in the outbox and clears the screen`() {
        pushPlan(PlanExercise("Bench Press", equipment = "barbell", sets = List(2) { PrescribedSet(reps = 6) }))
        val session = controller()
        session.startPlanned()
        val id = session.guidedExerciseId()!!
        session.logSet(id, 0.0, 6, null, null)
        session.finish()

        assertNull(session.draft.value)
        assertNull(session.guided.value)
        assertEquals(1, session.pending.value)
        val finished = store.outbox.sessions().single()
        assertEquals("Push", finished.name)
        assertEquals(today, finished.day)
        assertEquals("barbell", finished.exercises.single().equipment)
        // Six reps, no weight -- absent on the wire, not a zero.
        assertNull(finished.exercises.single().sets.single().weightKg)
        assertEquals(6, finished.exercises.single().sets.single().reps)
        // And nothing is left in progress to resume.
        assertNull(store.session)
    }

    @Test fun `a finished session is kept until the phone acknowledges it`() {
        val session = controller()
        session.startFreeWorkout()
        session.addExercise("Squat", "barbell", "squat-barbell")
        session.logSet(session.draft.value!!.exercises.single().id, 100.0, 5, null, null)
        session.finish()

        val sessionId = store.outbox.sessions().single().sessionId
        // A new process still owes it, because a send is not a receipt.
        assertEquals(1, SessionStore(ApplicationProvider.getApplicationContext()).outbox.count)
        store.acknowledge(sessionId)
        assertEquals(0, SessionStore(ApplicationProvider.getApplicationContext()).outbox.count)
    }

    @Test fun `the unit is the lifter's own and outlives the session`() {
        val session = controller()
        assertEquals(WeightUnit.POUNDS, session.unit.value)
        session.setUnit(WeightUnit.KILOGRAMS)
        assertEquals(WeightUnit.KILOGRAMS, controller().unit.value)
    }

    @Test fun `starting a plan needs a plan, and an empty one is not one`() {
        assertEquals(false, controller().startPlanned())
        pushPlan()
        assertEquals(false, controller().startPlanned())
        assertNotNull(controller().todaysPlan())
    }
}
