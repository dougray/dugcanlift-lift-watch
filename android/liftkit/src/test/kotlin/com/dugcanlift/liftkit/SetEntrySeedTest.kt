package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LastPerformed
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.PrescribedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the Log set screen starts at.
 *
 * The case this file is really for is the last block: a coach who prescribed reps and no weight gets
 * **no weight**, not 135 and not 0.
 */
class SetEntrySeedTest {
    private val lb = WeightUnit.POUNDS
    private val kg = WeightUnit.KILOGRAMS

    private fun set(weightKg: Double, reps: Int, rpe: Double? = null) =
        DraftSet(id = "s", orderIndex = 0, weightKg = weightKg, reps = reps, rpe = rpe)

    // ---- off plan -------------------------------------------------------------------------------

    @Test fun `off plan with no history starts at a loaded bar in the lifter's own unit`() {
        assertEquals(135.0, lb.fromKilograms(SetEntrySeed.of(lb).weightKg!!), 1e-6)
        assertEquals(60.0, kg.fromKilograms(SetEntrySeed.of(kg).weightKg!!), 1e-6)
        assertEquals(5, SetEntrySeed.of(lb).reps)
        assertEquals(8.0, SetEntrySeed.of(lb).rpe!!, 1e-9)
    }

    @Test fun `off plan a later set repeats the one before it`() {
        val seed = SetEntrySeed.of(lb, previousSet = set(lb.toKilograms(225.0), 3, 9.0))
        assertEquals(225.0, lb.fromKilograms(seed.weightKg!!), 1e-6)
        assertEquals(3, seed.reps)
        assertEquals(9.0, seed.rpe!!, 1e-9)
    }

    @Test fun `a repeated weight lands on the step a bar actually loads to`() {
        // 187 lb becomes 185: nobody has a 2 lb plate, and the crown steps in fives.
        val seed = SetEntrySeed.of(lb, previousSet = set(lb.toKilograms(187.0), 5))
        assertEquals(185.0, lb.fromKilograms(seed.weightKg!!), 1e-6)
        // In kilograms the step is 2.5, so 31 becomes 30.
        val metric = SetEntrySeed.of(kg, previousSet = set(31.0, 5))
        assertEquals(30.0, kg.fromKilograms(metric.weightKg!!), 1e-6)
    }

    // ---- the prescription wins, field by field ---------------------------------------------------

    @Test fun `a prescribed weight seeds the field exactly, never rounded`() {
        // 82.5 kg is a real prescription and rounding it to 82 would show a number nobody wrote.
        val seed = SetEntrySeed.of(kg, prescription = PrescribedSet(weightKg = 82.5, reps = 5))
        assertEquals(82.5, seed.weightKg!!, 1e-9)
        assertEquals(5, seed.reps)
    }

    @Test fun `reps from the plan and the weight from what was actually lifted`() {
        // PLAN-FORMAT's [null, 5]: five reps at a weight you pick.
        val seed = SetEntrySeed.of(
            lb,
            prescription = PrescribedSet(reps = 5),
            lastPerformed = LastPerformed(weightKg = lb.toKilograms(183.0), reps = 3),
        )
        assertEquals(5, seed.reps)
        assertEquals(185.0, lb.fromKilograms(seed.weightKg!!), 1e-6)
    }

    @Test fun `last time is only borrowed from on the first set of the exercise`() {
        val seed = SetEntrySeed.of(
            lb,
            prescription = PrescribedSet(reps = 5),
            previousSet = set(lb.toKilograms(200.0), 5),
            lastPerformed = LastPerformed(weightKg = lb.toKilograms(135.0), reps = 8),
        )
        // The set two minutes ago beats the session a week ago.
        assertEquals(200.0, lb.fromKilograms(seed.weightKg!!), 1e-6)
        assertEquals(5, seed.reps)
    }

    @Test fun `a prescribed RPE wins, and one nobody prescribed is not invented on plan`() {
        assertEquals(8.5, SetEntrySeed.of(lb, prescription = PrescribedSet(reps = 5, rpe = 8.5)).rpe!!, 1e-9)
        // On plan with no RPE anywhere, RPE is simply not recorded -- it is optional on the wire and
        // making one up would put a number in a lifter's log that nobody measured.
        assertNull(SetEntrySeed.of(lb, prescription = PrescribedSet(reps = 5)).rpe)
    }

    // ---- the case this file exists for ------------------------------------------------------------

    @Test fun `a prescription of reps with no weight seeds no weight at all`() {
        val seed = SetEntrySeed.of(lb, prescription = PrescribedSet(reps = 6))
        assertNull(seed.weightKg)
        assertEquals(6, seed.reps)
    }

    @Test fun `Doug's routine - four sets of six reps, no weight - never seeds a number`() {
        var previous: DraftSet? = null
        repeat(4) {
            val seed = SetEntrySeed.of(lb, prescription = PrescribedSet(reps = 6), previousSet = previous)
            assertNull("set ${it + 1} invented a weight", seed.weightKg)
            assertEquals(6, seed.reps)
            previous = DraftSet(id = "s$it", orderIndex = it, weightKg = 0.0, reps = seed.reps)
        }
    }

    @Test fun `a weightless set logged before does not become a weight for the next one`() {
        // A previous set stored with weightKg 0.0 is "no weight", not a zero to carry forward.
        assertNull(SetEntrySeed.of(lb, prescription = PrescribedSet(reps = 6),
            previousSet = set(0.0, 6)).weightKg)
    }

    // ---- sides ------------------------------------------------------------------------------------

    @Test fun `the side offered is the side the session suggested, and absent stays absent`() {
        assertEquals(LogSide.LEFT, SetEntrySeed.of(lb, side = LogSide.LEFT).side)
        assertNull(SetEntrySeed.of(lb).side)
    }
}
