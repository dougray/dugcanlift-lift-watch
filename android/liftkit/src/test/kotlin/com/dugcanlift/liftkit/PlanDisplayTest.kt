package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LastPerformed
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the session screen reads.
 *
 * **The blank-weight case is the one this file exists for.** Doug's own routine is four sets of six
 * reps with no weight, and it is the shape the presence-bit wire was written to protect: a weight
 * the coach did not prescribe must reach the screen as no weight, never as a zero.
 *
 * The multiplication sign is an ASCII `x`, matching what LIFT for Apple Watch writes, so the two
 * wrists read the same line.
 */
class PlanDisplayTest {
    private val lb = WeightUnit.POUNDS
    private val kg = WeightUnit.KILOGRAMS

    // ---- blank stays blank --------------------------------------------------------------------

    @Test fun `reps with no weight show reps and no weight at all`() {
        val set = PrescribedSet(reps = 6)
        assertEquals("6 reps", set.headline(lb))
        assertNull(set.weightText(lb))
        assertFalse(set.headline(lb).contains("0"))
    }

    @Test fun `Doug's routine - four sets of six reps, no weight - never says zero`() {
        val plan = Plan(
            "p", 1, "Push", PlanSource.ROUTINE, "2026-09-25",
            listOf(PlanExercise("Bench Press", sets = List(4) { PrescribedSet(reps = 6) })),
        )
        val session = GuidedSession.start(plan)
        assertEquals("6 reps", session.currentPrescription!!.headline(lb))
        assertEquals("1/4", session.positionText)
        assertEquals(4, plan.totalSetCount)
    }

    @Test fun `a set that prescribes nothing at all is a dash, not a zero`() {
        assertEquals("—", PrescribedSet().headline(lb))
        assertTrue(PrescribedSet().isEmpty)
    }

    @Test fun `a weight with no reps carries its unit so it cannot be read as reps`() {
        assertEquals("185 lb", PrescribedSet(weightKg = 83.915).headline(lb))
        assertEquals("83.9 kg", PrescribedSet(weightKg = 83.915).headline(kg))
    }

    @Test fun `a side is not a number, so a set naming only a side still prescribes nothing`() {
        assertTrue(PrescribedSet(side = LogSide.LEFT).isEmpty)
    }

    // ---- the big line -------------------------------------------------------------------------

    @Test fun `weight and reps read with an ASCII x, as the Apple watch writes it`() {
        assertEquals("185 x 5", PrescribedSet(weightKg = 83.915, reps = 5).headline(lb))
    }

    @Test fun `a side is appended with a middle dot and one letter`() {
        val set = PrescribedSet(weightKg = 13.608, reps = 8)
        assertEquals("30 x 8 · L", set.headline(lb, LogSide.LEFT))
        assertEquals("30 x 8 · R", set.headline(lb, LogSide.RIGHT))
    }

    @Test fun `the summary puts RPE with the numbers and the side last`() {
        val set = PrescribedSet(weightKg = 13.608, reps = 8, rpe = 8.0)
        assertEquals("30 x 8 @8", set.summary(lb))
        assertEquals("30 x 8 @8 · R", set.summary(lb, LogSide.RIGHT))
    }

    // ---- rounding -----------------------------------------------------------------------------

    @Test fun `whole numbers stay whole and a real half keeps its decimal`() {
        assertEquals("185", PlanFormat.weight(83.915, lb))
        assertEquals("82.5", PlanFormat.weight(82.5, kg))
        assertEquals("100", PlanFormat.weight(100.0, kg))
        assertEquals("8", PlanFormat.rpe(8.0))
        assertEquals("8.5", PlanFormat.rpe(8.5))
    }

    // ---- last performed -----------------------------------------------------------------------

    @Test fun `last time's actual reads as the reference line under the prescription`() {
        assertEquals("185x5 @8", LastPerformed(83.915, 5, 8.0, "2026-09-14").summary(lb))
        assertEquals("185x5", LastPerformed(83.915, 5).summary(lb))
        assertEquals("5 reps", LastPerformed(reps = 5).summary(lb))
        assertEquals("185 lb", LastPerformed(weightKg = 83.915).summary(lb))
    }

    @Test fun `a record holding no numbers shows nothing rather than a dash pretending to be history`() {
        assertNull(LastPerformed().summary(lb))
        assertNull(LastPerformed(performedOn = "2026-09-14").summary(lb))
    }

    // ---- the day the plan is for ---------------------------------------------------------------

    @Test fun `yesterday's plan is not today's, and an undated plan always is`() {
        val dated = Plan("p", 1, "Push", PlanSource.ROUTINE, "2026-09-24", emptyList())
        assertFalse(dated.isScheduledFor("2026-09-25"))
        assertTrue(dated.isScheduledFor("2026-09-24"))
        assertTrue(Plan("p", 1, "Push", PlanSource.ROUTINE, null, emptyList()).isScheduledFor("2026-09-25"))
    }

    @Test fun `the plan's total is sets to perform, so an each-side exercise counts twice`() {
        val plan = Plan(
            "p", 1, "Legs", PlanSource.COACH_PLAN, null,
            listOf(
                PlanExercise("Squat", sets = List(3) { PrescribedSet(reps = 5) }),
                PlanExercise("Split Squat", sets = List(3) { PrescribedSet(reps = 8) }, eachSide = true),
            ),
        )
        assertEquals(9, plan.totalSetCount)
    }

    @Test fun `a source says where the plan came from in the coach's words`() {
        assertEquals("Routine", PlanSource.ROUTINE.displayName)
        assertEquals("From your coach", PlanSource.COACH_PLAN.displayName)
    }

    @Test fun `an exercise's name carries its equipment, and no parentheses when it has none`() {
        assertEquals("Lat Pulldown (Cable)", PlanExercise("Lat Pulldown", equipment = "cable").displayName)
        assertEquals("Pull Up", PlanExercise("Pull Up").displayName)
        assertEquals("Pull Up", PlanExercise("Pull Up", equipment = "  ").displayName)
    }
}
