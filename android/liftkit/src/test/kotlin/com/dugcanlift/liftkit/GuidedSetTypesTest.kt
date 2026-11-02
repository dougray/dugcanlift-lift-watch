package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.GuidedSession.RestAfter
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import com.dugcanlift.liftkit.link.SetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A superset trained round by round on the wrist, and what rest each set starts. The cases are LIFT
 * iPhone's `SessionRestTests` ones, because the rule is ported from its `SessionRest`.
 */
class GuidedSetTypesTest {

    private fun plan(vararg exercises: PlanExercise) =
        Plan("plan-1", 1, "Upper B", PlanSource.COACH_PLAN, null, exercises.toList())

    private fun sets(n: Int, rest: Int? = 120) = List(n) { PrescribedSet(reps = 10, restSeconds = rest) }

    private val bench = PlanExercise("Dumbbell Bench Press", sets = sets(3), group = 1)
    private val row = PlanExercise("Seated Cable Row", sets = sets(3), group = 1)
    private val curl = PlanExercise("Barbell Curl", sets = sets(2))

    /** Logs [n] sets wherever the session says, returning each set's exercise name and rest. */
    private fun walk(start: GuidedSession, n: Int, side: LogSide? = null): Pair<GuidedSession, List<Pair<String, RestAfter>>> {
        var session = start
        val out = mutableListOf<Pair<String, RestAfter>>()
        repeat(n) {
            val name = session.currentExercise!!.name
            val recorded = session.recordSet(side)
            out += name to recorded.rest
            session = recorded.session
        }
        return session to out
    }

    @Test fun `a superset goes round by round and rests after the round, not between its exercises`() {
        val (_, log) = walk(GuidedSession.start(plan(bench, row, curl)), 8)
        assertEquals(
            listOf(
                "Dumbbell Bench Press" to RestAfter.None, "Seated Cable Row" to RestAfter.Rest(120),
                "Dumbbell Bench Press" to RestAfter.None, "Seated Cable Row" to RestAfter.Rest(120),
                "Dumbbell Bench Press" to RestAfter.None, "Seated Cable Row" to RestAfter.Rest(120),
                "Barbell Curl" to RestAfter.Rest(120), "Barbell Curl" to RestAfter.Rest(120),
            ),
            log,
        )
    }

    @Test fun `a circuit of uneven members skips the one that has run out`() {
        val a = PlanExercise("A", sets = sets(2), group = 4)
        val b = PlanExercise("B", sets = sets(1), group = 4)
        val c = PlanExercise("C", sets = sets(2), group = 4)
        val (session, log) = walk(GuidedSession.start(plan(a, b, c)), 5)
        assertEquals(listOf("A", "B", "C", "A", "C"), log.map { it.first })
        assertEquals(listOf(RestAfter.None, RestAfter.None, RestAfter.Rest(120), RestAfter.None, RestAfter.Rest(120)), log.map { it.second })
        assertTrue(session.isComplete)
    }

    @Test fun `starting a member out of order still finishes the round before resting`() {
        var session = GuidedSession.start(plan(bench, row)).select(1)
        val first = session.recordSet()
        assertEquals(RestAfter.None, first.rest)
        assertEquals("Dumbbell Bench Press", first.session.currentExercise!!.name)
        session = first.session
        assertEquals(RestAfter.Rest(120), session.recordSet().rest)
    }

    @Test fun `an each-side member does both sides inside its round`() {
        val split = PlanExercise("Split Squat", sets = sets(2), eachSide = true, group = 1)
        val jump = PlanExercise("Box Jump", sets = sets(2), group = 1)
        val (_, log) = walk(GuidedSession.start(plan(split, jump)), 6)
        assertEquals(listOf("Split Squat", "Split Squat", "Box Jump", "Split Squat", "Split Squat", "Box Jump"), log.map { it.first })
        assertEquals(RestAfter.Rest(120), log[2].second)
        assertEquals(RestAfter.None, log[0].second)
    }

    @Test fun `no rest before a drop set, fifteen seconds before rest-pause`() {
        val pushdown = PlanExercise(
            "Triceps Pushdown",
            sets = listOf(
                PrescribedSet(reps = 12, restSeconds = 90),
                PrescribedSet(reps = 10, restSeconds = 90, kind = SetKind.DROP),
                PrescribedSet(reps = 8, restSeconds = 90, kind = SetKind.REST_PAUSE),
                PrescribedSet(reps = 8, restSeconds = 90),
            ),
        )
        val (_, log) = walk(GuidedSession.start(plan(pushdown, curl)), 4)
        assertEquals(
            // The last is the pushdown's own 90: a rest is the set just performed's, never the next exercise's.
            listOf(RestAfter.None, RestAfter.Rest(GuidedSession.REST_PAUSE_SECONDS), RestAfter.Rest(90), RestAfter.Rest(90)),
            log.map { it.second },
        )
        assertEquals(15, GuidedSession.REST_PAUSE_SECONDS)
    }

    @Test fun `a drop set in a superset's second round follows the round's last set at once`() {
        val a = PlanExercise("A", sets = listOf(PrescribedSet(reps = 10), PrescribedSet(reps = 8, kind = SetKind.DROP)), group = 1)
        val b = PlanExercise("B", sets = sets(2, rest = null), group = 1)
        val (_, log) = walk(GuidedSession.start(plan(a, b)), 2)
        assertEquals(listOf(RestAfter.None, RestAfter.None), log.map { it.second })
    }

    @Test fun `a plan with no set types rests and moves on exactly as before`() {
        val plainBench = bench.copy(group = null)
        val plainRow = row.copy(group = null)
        val (_, log) = walk(GuidedSession.start(plan(plainBench, plainRow)), 6)
        assertEquals(List(3) { "Dumbbell Bench Press" } + List(3) { "Seated Cable Row" }, log.map { it.first })
        assertTrue(log.all { it.second == RestAfter.Rest(120) })
    }

    @Test fun `a lone group number is no superset`() {
        val (_, log) = walk(GuidedSession.start(plan(bench, curl)), 3)
        assertEquals(List(3) { "Dumbbell Bench Press" }, log.map { it.first })
    }

    @Test fun `the last set of the whole plan takes its own rest`() {
        val (session, log) = walk(GuidedSession.start(plan(bench, row)), 6)
        assertTrue(session.isComplete)
        assertEquals(RestAfter.Rest(120), log.last().second)
    }
}
