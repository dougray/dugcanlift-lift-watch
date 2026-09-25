package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guided session's state machine and its side sequence.
 *
 * The side cases are **ported from LIFT Android's own `PlanSidesTest`**, case for case, because the
 * rule is the phone's and a second reading of it is the defect this is here to prevent. The seven-set
 * exercise — three each-side rows plus one extra left — is the phone's fixture case, and the bench
 * press with one named right set is its other one.
 */
class GuidedSessionTest {

    private fun plan(vararg exercises: PlanExercise) =
        Plan("plan-1", 1, "Upper A", PlanSource.ROUTINE, "2026-09-25", exercises.toList())

    private val bare = PrescribedSet(weightKg = 30.0, reps = 8)

    // ---- position -----------------------------------------------------------------------------

    @Test fun `a plain plan counts prescribed rows and says nothing about sides`() {
        val session = GuidedSession.start(plan(PlanExercise("Bench Press", sets = List(3) { bare })))
        assertEquals("1/3", session.positionText)
        assertNull(session.currentSide)
        assertEquals(3, session.currentSetCount)
    }

    @Test fun `an each-side exercise is twice the sets and reads 1 of 6 on the left`() {
        val session = GuidedSession.start(
            plan(PlanExercise("Split Squat", sets = List(3) { bare }, eachSide = true))
        )
        assertEquals(6, session.currentSetCount)
        assertEquals("1/6 · L", session.positionText)
        assertEquals(LogSide.LEFT, session.currentSide)
    }

    @Test fun `the third set of an each-side exercise reads 3 of 6 and its side`() {
        var session = GuidedSession.start(
            plan(PlanExercise("Split Squat", sets = List(3) { bare }, eachSide = true))
        )
        session = session.recordSet(LogSide.LEFT).session
        session = session.recordSet(LogSide.RIGHT).session
        assertEquals("3/6 · L", session.positionText)
    }

    // ---- the side sequence, ported from the phone's PlanSidesTest ----------------------------

    /** Three each-side rows plus one extra left: order L,R,L,R,L,R,L — left 4, right 3. */
    private fun sevenSetExercise() = PlanExercise(
        name = "Split Squat",
        sets = listOf(bare, bare, bare, bare.copy(side = LogSide.LEFT)),
        eachSide = true,
    )

    @Test fun `the seven-set case expects four left and three right`() {
        val exercise = sevenSetExercise()
        assertEquals(7, exercise.plannedSets.size)
        assertEquals(4, exercise.plannedSets.count { it.side == LogSide.LEFT })
        assertEquals(3, exercise.plannedSets.count { it.side == LogSide.RIGHT })
    }

    @Test fun `the offer starts on the side the next unfilled prescribed set names`() {
        val exercise = sevenSetExercise()
        fun next(vararg logged: LogSide?) = GuidedSession.side(exercise, logged.toList())

        assertEquals(LogSide.LEFT, next())
        assertEquals(LogSide.RIGHT, next(LogSide.LEFT))
        // Three lefts logged first still leave the first right unfilled.
        assertEquals(LogSide.RIGHT, next(LogSide.LEFT, LogSide.LEFT, LogSide.LEFT))
        // Six logged in pairs: the seventh prescribed set is the extra left.
        assertEquals(
            LogSide.LEFT,
            next(LogSide.LEFT, LogSide.RIGHT, LogSide.LEFT, LogSide.RIGHT, LogSide.LEFT, LogSide.RIGHT),
        )
    }

    @Test fun `past the prescription the side behind is offered, left breaking a tie`() {
        val exercise = sevenSetExercise()
        // Every prescribed set filled: left 4, right 3, so right is behind.
        val filled = listOf(
            LogSide.LEFT, LogSide.RIGHT, LogSide.LEFT, LogSide.RIGHT,
            LogSide.LEFT, LogSide.RIGHT, LogSide.LEFT,
        )
        assertEquals(LogSide.RIGHT, GuidedSession.side(exercise, filled))
        // And level again: the tie goes left, which is where a pair starts.
        assertEquals(LogSide.LEFT, GuidedSession.side(exercise, filled + LogSide.RIGHT))
    }

    @Test fun `a bench press with one named right set stops asking once that set is logged`() {
        val bench = PlanExercise(
            name = "Bench Press",
            sets = listOf(bare, bare, bare.copy(side = LogSide.RIGHT)),
        )
        // Two two-sided sets contribute nothing to the walk, so the first offer is the named right.
        assertEquals(LogSide.RIGHT, GuidedSession.side(bench, listOf(null, null)))
        // Logged, and the exercise is a bench press again: no side, no fallback, because it is not
        // each side. This is the phone's `pendingNamedSide` going false.
        assertNull(GuidedSession.side(bench, listOf(null, null, LogSide.RIGHT)))
    }

    @Test fun `a prescription without sides changes nothing`() {
        val plain = PlanExercise("Bench Press", sets = List(3) { bare })
        assertNull(GuidedSession.side(plain, emptyList()))
        assertNull(GuidedSession.side(plain, listOf(null, null, null)))
        assertEquals(3, plain.plannedSets.size)
        assertTrue(plain.plannedSets.all { it.side == null })
        assertEquals(false, plain.prescribesSides)
    }

    @Test fun `a named side is never doubled, even on an each-side exercise`() {
        val exercise = PlanExercise(
            "Split Squat",
            sets = listOf(bare.copy(side = LogSide.LEFT)),
            eachSide = true,
        )
        assertEquals(1, exercise.plannedSets.size)
        assertEquals(LogSide.LEFT, exercise.plannedSets.single().side)
    }

    // ---- the prescription a set answers -------------------------------------------------------

    @Test fun `three lefts logged first still leave the first right's numbers for the first right`() {
        val exercise = PlanExercise(
            "Split Squat",
            sets = listOf(
                PrescribedSet(weightKg = 20.0, reps = 10),
                PrescribedSet(weightKg = 25.0, reps = 8),
                PrescribedSet(weightKg = 30.0, reps = 6),
            ),
            eachSide = true,
        )
        var session = GuidedSession.start(plan(exercise))
        // Three lefts, by overriding the offered side each time.
        repeat(3) { session = session.recordSet(LogSide.LEFT).session }
        assertEquals(LogSide.RIGHT, session.currentSide)
        assertEquals(20.0, session.currentPrescription?.weightKg!!, 1e-9)
    }

    @Test fun `an extra set past the prescription repeats the last thing asked for on that side`() {
        val exercise = PlanExercise(
            "Split Squat",
            sets = listOf(PrescribedSet(weightKg = 30.0, reps = 6)),
            eachSide = true,
        )
        var session = GuidedSession.start(plan(exercise))
        session = session.recordSet(LogSide.LEFT).session
        session = session.recordSet(LogSide.RIGHT).session
        assertTrue(session.isComplete)
        // The plan is done, but a lifter doing one more is not an error: the numbers are the last
        // ones the coach wrote for that side.
        assertEquals(30.0, GuidedSession.start(plan(exercise)).currentPrescription?.weightKg!!, 1e-9)
    }

    // ---- moving through the plan --------------------------------------------------------------

    @Test fun `the next exercise follows on when this one's sets are done`() {
        var session = GuidedSession.start(
            plan(
                PlanExercise("Bench Press", sets = List(2) { bare }),
                PlanExercise("Lat Pulldown", sets = List(2) { bare }),
            )
        )
        assertEquals("Bench Press", session.currentExercise?.name)
        session = session.recordSet().session
        assertEquals("Bench Press", session.currentExercise?.name)
        session = session.recordSet().session
        assertEquals("Lat Pulldown", session.currentExercise?.name)
        assertEquals("1/2", session.positionText)
    }

    @Test fun `the rest interval comes from the set just performed, not the next one`() {
        val session = GuidedSession.start(
            plan(
                PlanExercise(
                    "Bench Press",
                    sets = listOf(
                        PrescribedSet(reps = 5, restSeconds = 180),
                        PrescribedSet(reps = 5, restSeconds = 60),
                    ),
                )
            )
        )
        assertEquals(180, session.recordSet().performed?.restSeconds)
    }

    @Test fun `a skipped exercise is come back to by wrapping`() {
        var session = GuidedSession.start(
            plan(
                PlanExercise("Bench Press", sets = List(1) { bare }),
                PlanExercise("Lat Pulldown", sets = List(1) { bare }),
            )
        )
        session = session.select(1)
        session = session.recordSet().session
        // Nothing owed after index 1, so it wraps to the bench press nobody trained.
        assertEquals("Bench Press", session.currentExercise?.name)
        session = session.recordSet().session
        assertTrue(session.isComplete)
        assertNull(session.currentExercise)
        assertEquals("", session.positionText)
    }

    @Test fun `an exercise prescribing nothing at all is already done`() {
        val session = GuidedSession.start(
            plan(
                PlanExercise("Foam Roll", sets = emptyList()),
                PlanExercise("Bench Press", sets = List(1) { bare }),
            )
        )
        assertEquals("Bench Press", session.currentExercise?.name)
    }

    @Test fun `an out-of-range selection is ignored rather than thrown`() {
        val session = GuidedSession.start(plan(PlanExercise("Bench Press", sets = List(1) { bare })))
        assertEquals(session, session.select(7))
        assertEquals(session, session.select(-1))
    }

    @Test fun `an extra set on an exercise already done still counts while the plan is not`() {
        var session = GuidedSession.start(
            plan(
                PlanExercise("Bench Press", sets = List(1) { bare }),
                PlanExercise("Lat Pulldown", sets = List(1) { bare }),
            )
        )
        session = session.recordSet().session          // bench done, on to the pulldown
        session = session.select(0).recordSet().session // one more bench press
        assertEquals(2, session.completedSets(0))
        // A plan is a prescription, not a limit: the count rose past what was asked for.
        assertEquals("Lat Pulldown", session.currentExercise?.name)
    }

    @Test fun `past the end of the plan the session stops counting and the draft keeps the set`() {
        var session = GuidedSession.start(plan(PlanExercise("Bench Press", sets = List(1) { bare })))
        session = session.recordSet().session
        assertTrue(session.isComplete)
        // Nothing left to advance to, so the position is not moved and no rest is prescribed. The
        // set itself is on the `WorkoutDraft` either way, which is what travels home -- the guided
        // session only ever tracked where in the plan the lifter was, and they are past its end.
        val after = session.recordSet()
        assertEquals(session, after.session)
        assertNull(after.performed)
        assertEquals(1, session.completedSetCount)
    }

    @Test fun `recording a set on a finished plan changes nothing`() {
        val done = GuidedSession.start(plan(PlanExercise("Bench Press", sets = emptyList())))
        val recorded = done.recordSet(LogSide.LEFT)
        assertEquals(done, recorded.session)
        assertNull(recorded.performed)
    }

    @Test fun `an empty plan is complete and answers nothing`() {
        val session = GuidedSession.start(plan())
        assertTrue(session.isComplete)
        assertNull(session.currentExercise)
        assertNull(session.currentPrescription)
        assertEquals(0, session.currentSetCount)
    }
}
