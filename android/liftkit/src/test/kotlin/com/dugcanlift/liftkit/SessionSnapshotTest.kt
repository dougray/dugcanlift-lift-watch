package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session surviving the app being killed. On Wear that is not a nicety: the radio is off unless
 * LIFT is open, so a plan lost to a process death is a plan the lifter walks back to their phone for.
 */
class SessionSnapshotTest {

    private val plan = Plan(
        "plan-1", 3, "Legs", PlanSource.COACH_PLAN, "2026-09-25",
        listOf(
            PlanExercise("Split Squat", sets = List(2) { PrescribedSet(reps = 8) }, eachSide = true),
            PlanExercise("Leg Press", equipment = "machine", sets = List(3) { PrescribedSet(reps = 12) }),
        ),
    )

    private fun draft() = WorkoutDraft(
        id = "session-1", name = "Legs", startedAtEpochSeconds = 1_758_800_000L,
        updatedAtEpochSeconds = 1_758_800_000L,
    ).addExercise("plan:0", "Split Squat", nowEpochSeconds = 1_758_800_001L) { "ex-0" }

    @Test fun `a guided session in progress comes back with its position, its sides and its plan`() {
        var guided = GuidedSession.start(plan)
        guided = guided.recordSet(LogSide.LEFT).session
        guided = guided.recordSet(LogSide.RIGHT).session
        guided = guided.recordSet(LogSide.LEFT).session
        val rest = RestTimer(180).started(1_758_800_500_000L)

        val text = SessionSnapshot.encode(SessionSnapshot.of(draft(), guided, rest))
        val back = SessionSnapshot.decode(text)!!

        assertEquals(guided, back.guidedSession())
        assertEquals(rest, back.restTimer())
        assertEquals("Split Squat", back.draft.exercises.single().name)
        // And the position is exactly where the lifter was: two each-side rows are four sets, and
        // the fourth is the right one still owed.
        assertEquals("4/4 · R", back.guidedSession()!!.positionText)
    }

    @Test fun `the plan comes back through its own wire codec, sides included`() {
        val back = SessionSnapshot.decode(
            SessionSnapshot.encode(SessionSnapshot.of(draft(), GuidedSession.start(plan), RestTimer()))
        )!!
        assertEquals(plan, back.guidedSession()!!.plan)
        assertTrue(back.guidedSession()!!.plan.exercises[0].eachSide)
    }

    @Test fun `a two-sided set is stored as zero and comes back as no side at all`() {
        val plain = Plan("p", 1, "Push", PlanSource.ROUTINE, null,
            listOf(PlanExercise("Bench Press", sets = List(2) { PrescribedSet(reps = 6) })))
        val guided = GuidedSession.start(plain).recordSet().session
        val snapshot = SessionSnapshot.of(draft(), guided, RestTimer())
        assertEquals(listOf(listOf(0)), snapshot.loggedSides)
        assertNull(SessionSnapshot.decode(SessionSnapshot.encode(snapshot))!!.guidedSession()!!.loggedSides[0][0])
    }

    @Test fun `a free-entry session has no plan and comes back without one`() {
        val back = SessionSnapshot.decode(
            SessionSnapshot.encode(SessionSnapshot.of(draft(), null, RestTimer()))
        )!!
        assertNull(back.planWire)
        assertNull(back.guidedSession())
        assertEquals("session-1", back.draft.id)
    }

    @Test fun `a logged set keeps its side and its weight through the round trip`() {
        val logged = draft().logSet(
            "ex-0", weightKg = 20.0, reps = 8, rpe = 8.5, side = LogSide.RIGHT,
            nowEpochSeconds = 1_758_800_100L,
        ) { "set-0" }
        val back = SessionSnapshot.decode(
            SessionSnapshot.encode(SessionSnapshot.of(logged, null, RestTimer()))
        )!!
        val set = back.draft.exercises.single().sets.single()
        assertEquals(LogSide.RIGHT, set.side)
        assertEquals(20.0, set.weightKg, 1e-9)
        assertEquals(8.5, set.rpe!!, 1e-9)
    }

    @Test fun `nothing stored, or something unreadable, is simply no session in progress`() {
        assertNull(SessionSnapshot.decode(null))
        assertNull(SessionSnapshot.decode(""))
        assertNull(SessionSnapshot.decode("{\"nope\":1}"))
        assertNull(SessionSnapshot.decode("not json at all"))
    }

    @Test fun `a plan whose bytes will not decode costs the guidance and never the draft`() {
        val snapshot = SessionSnapshot.of(draft(), GuidedSession.start(plan), RestTimer())
            .copy(planWire = "!!!not base64!!!")
        assertNull(snapshot.guidedSession())
        assertEquals(1, snapshot.draft.exercises.size)
    }

    // ---- the plan the phone pushed --------------------------------------------------------------

    @Test fun `a stored plan round trips through its own wire bytes`() {
        val stored = StoredPlan.of(plan, 1_758_800_000L)
        val back = StoredPlan.decode(StoredPlan.encode(stored))!!
        assertEquals(plan, back.plan())
        assertEquals("plan-1", back.planId)
        assertEquals(3, back.revision)
    }

    @Test fun `a newer revision replaces, the same or older is ignored, a new plan always wins`() {
        val held = StoredPlan.of(plan, 0L)
        assertTrue(StoredPlan.accepts(null, plan))
        assertEquals(false, StoredPlan.accepts(held, plan))
        assertEquals(false, StoredPlan.accepts(held, plan.copy(revision = 2)))
        assertTrue(StoredPlan.accepts(held, plan.copy(revision = 4)))
        assertTrue(StoredPlan.accepts(held, plan.copy(planId = "plan-2", revision = 1)))
    }

    @Test fun `a stored plan that will not read back is nothing held, not a crash`() {
        assertNull(StoredPlan.decode("garbage"))
        assertNotNull(StoredPlan.decode(StoredPlan.encode(StoredPlan.of(plan, 0L))))
        assertNull(StoredPlan("!!", "p", 1, 0L).plan())
    }
}
