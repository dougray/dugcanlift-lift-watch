package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import com.dugcanlift.liftkit.link.SetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a coach's set types put on the wrist, and which set they are said about.
 *
 * The label cases are LIFT Android's `SetTypesTest` ones for `labels`, `letters` and `heading`,
 * because the rule is ported from there and a second reading of it is the defect this prevents.
 */
class SetTypeLabelsTest {

    private fun plan(vararg exercises: PlanExercise) =
        Plan("plan-1", 1, "Upper B", PlanSource.COACH_PLAN, null, exercises.toList())

    private fun lift(name: String, group: Int? = null, sets: List<PrescribedSet> = listOf(PrescribedSet(reps = 10))) =
        PlanExercise(name, sets = sets, group = group)

    // ---- group labels ------------------------------------------------------------------------

    @Test fun `two is a superset, three or more a circuit, and members count from 1`() {
        val labels = SetTypeLabels.labels(listOf(1, 1, null, 2, 2, 2))
        assertEquals("Superset A", labels[0]!!.group)
        assertEquals("A1", labels[0]!!.member)
        assertTrue(labels[0]!!.first)
        assertEquals("A2", labels[1]!!.member)
        assertFalse(labels[1]!!.first)
        assertNull(labels[2])
        assertEquals("Circuit B", labels[3]!!.group)
        assertEquals(listOf("B1", "B2", "B3"), labels.drop(3).map { it!!.member })
        assertEquals(3, labels[5]!!.size)
    }

    @Test fun `the wrist's one line is the group then the place in it`() {
        val labels = plan(lift("Dumbbell Bench Press", 1), lift("Seated Cable Row", 1)).groupLabels
        assertEquals("Superset A · A1", labels[0]!!.line)
        assertEquals("Superset A · A2", labels[1]!!.line)
        assertEquals("Circuit A · A3", SetTypeLabels.labels(listOf(5, 5, 5))[2]!!.line)
    }

    @Test fun `a group of one is no group, and an ungrouped plan has no labels at all`() {
        assertEquals(listOf(null, null, null), SetTypeLabels.labels(listOf(1, null, 1)))
        assertEquals(listOf(null, null), plan(lift("Bench"), lift("Row")).groupLabels)
    }

    @Test fun `letters run past Z as a spreadsheet's do`() {
        assertEquals("A", SetTypeLabels.letters(0))
        assertEquals("Z", SetTypeLabels.letters(25))
        assertEquals("AA", SetTypeLabels.letters(26))
        assertEquals("AB", SetTypeLabels.letters(27))
    }

    @Test fun `a heading names the member before the lift`() {
        val label = SetTypeLabels.labels(listOf(1, 1))[0]
        assertEquals("A1 · Bench Press (Dumbbell)", SetTypeLabels.heading("Bench Press (Dumbbell)", label))
        assertEquals("Bench Press", SetTypeLabels.heading("Bench Press", null))
    }

    // ---- set kinds ---------------------------------------------------------------------------

    @Test fun `the kinds read in LIFT Android's words`() {
        assertEquals("drop", SetKind.DROP.label)
        assertEquals("rest-pause", SetKind.REST_PAUSE.label)
        assertEquals("Drop", SetKind.DROP.title)
        assertEquals("Rest-pause", SetKind.REST_PAUSE.title)
        assertEquals(" drop", SetTypeLabels.suffix(SetKind.DROP))
        assertEquals("", SetTypeLabels.suffix(null))
    }

    @Test fun `a summary says side then kind, and a straight set says neither`() {
        val set = PrescribedSet(weightKg = 30.0, reps = 8, rpe = 8.0)
        assertEquals("30 x 8 @8 · L drop", set.summary(WeightUnit.KILOGRAMS, LogSide.LEFT, SetKind.DROP))
        assertEquals("30 x 8 @8 rest-pause", set.summary(WeightUnit.KILOGRAMS, null, SetKind.REST_PAUSE))
        assertEquals("30 x 8 @8", set.summary(WeightUnit.KILOGRAMS))
        // Blank stays blank with a kind beside it, too.
        assertEquals("10 reps drop", PrescribedSet(reps = 10).summary(WeightUnit.KILOGRAMS, null, SetKind.DROP))
    }

    // ---- which set the kind is said about ---------------------------------------------------

    private val pushdown = lift(
        "Triceps Pushdown",
        sets = listOf(
            PrescribedSet(weightKg = 30.0, reps = 12),
            PrescribedSet(weightKg = 20.0, reps = 10, kind = SetKind.DROP),
            PrescribedSet(weightKg = 20.0, reps = 6, kind = SetKind.REST_PAUSE),
        ),
    )

    @Test fun `the kind follows the set about to be performed`() {
        var session = GuidedSession.start(plan(pushdown))
        assertNull(session.currentKind)
        session = session.recordSet().session
        assertEquals(SetKind.DROP, session.currentKind)
        session = session.recordSet().session
        assertEquals(SetKind.REST_PAUSE, session.currentKind)
    }

    @Test fun `an exercise's first set is straight even when the plan in memory says otherwise`() {
        val firstDrop = lift("Curl", sets = listOf(PrescribedSet(reps = 8, kind = SetKind.DROP), PrescribedSet(reps = 4)))
        val session = GuidedSession.start(plan(firstDrop))
        assertNull(session.currentKind)
        assertNull(firstDrop.plannedSets.first().kind)
    }

    @Test fun `an each-side drop set is a drop on both sides`() {
        val split = PlanExercise(
            "Split Squat",
            sets = listOf(PrescribedSet(reps = 8), PrescribedSet(reps = 6, kind = SetKind.DROP)),
            eachSide = true,
        )
        assertEquals(listOf(null, null, SetKind.DROP, SetKind.DROP), split.plannedSets.map { it.kind })
        var session = GuidedSession.start(plan(split))
        session = session.recordSet(LogSide.LEFT).session.recordSet(LogSide.RIGHT).session
        assertEquals("3/4 · L", session.positionText)
        assertEquals(SetKind.DROP, session.currentKind)
        session = session.recordSet(LogSide.LEFT).session
        assertEquals(SetKind.DROP, session.currentKind)
    }

    @Test fun `an extra set past the prescription is nobody's drop set`() {
        var session = GuidedSession.start(plan(pushdown, lift("Curl")))
        repeat(3) { session = session.recordSet().session }
        // Back to the finished pushdown for one more: the numbers repeat, the word does not.
        session = session.select(0)
        assertEquals(6, session.currentPrescription!!.reps)
        assertNull(session.currentKind)
    }

    @Test fun `a plan with no set types guides exactly as before`() {
        val session = GuidedSession.start(plan(lift("Bench"), lift("Row")))
        assertNull(session.currentKind)
        assertEquals("1/1", session.positionText)
        assertEquals(listOf(null, null), session.plan.groupLabels)
    }
}
