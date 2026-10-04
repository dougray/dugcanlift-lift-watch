package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LastPerformed
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.PrescribedSet

/**
 * What the Log set screen's fields start at.
 *
 * A value type with no view in it, for the reason [GuidedSession] and [RestTimer] are: this is the
 * whole of "logging a set pre-fills from the prescription", it decides the numbers a lifter actually
 * puts in their log, and a rule inside a `remember` cannot be tested.
 *
 * **The prescription wins field by field**, because every one of its fields is optional: `[null, 5]`
 * is five reps at a weight you pick, so the reps come from the plan and the weight from what was
 * last lifted. A prescribed weight seeds the field **exactly**, not rounded to the nearest five as a
 * repeat of the last set is — one turn of the crown to correct beats a field that disagrees with the
 * screen above it.
 *
 * ## No weight is a real answer here, and that is a deliberate difference from the Apple watch
 *
 * LIFT for Apple Watch starts this field at 135 whatever the plan says, because its `DraftSet.weightKg`
 * is a plain `Double` and cannot express "none". Wear's can ([DraftSet.weightKg] is sent through
 * [toLoggedSet], and `LoggedSet.weightKg` is nullable precisely for this), so when the coach
 * prescribed **no** weight and there is no history to borrow one from, [weightKg] comes back `null` and
 * the screen shows a dash rather than a number nobody asked for. Doug's own routine is four sets of six
 * reps with no weight; 135 lb of it is not what he meant, and a zero would be worse.
 *
 * With no plan at all the number is [FREE_ENTRY_POUNDS] / [FREE_ENTRY_KILOGRAMS] — a loaded bar, which
 * is where the Apple watch starts too. Nobody said anything, so starting somewhere useful is not
 * inventing a claim; a coach saying "no weight" is.
 */
data class SetEntrySeed(
    /** `null` is "no weight", a state the screen shows as a dash and the wire sends as absent. */
    val weightKg: Double?,
    val reps: Int,
    val rpe: Double?,
    val side: LogSide?,
) {
    companion object {
        const val DEFAULT_REPS = 5
        const val DEFAULT_RPE = 8.0

        /** A bar with a plate each side, in the lifter's own unit. The Apple watch's 135. */
        const val FREE_ENTRY_POUNDS = 135.0
        const val FREE_ENTRY_KILOGRAMS = 60.0

        /** The step a *borrowed* number is rounded to — a plate-loaded bar does not go in grams. */
        const val STEP_POUNDS = 5.0
        const val STEP_KILOGRAMS = 2.5

        fun step(unit: WeightUnit): Double =
            if (unit == WeightUnit.POUNDS) STEP_POUNDS else STEP_KILOGRAMS

        /**
         * @param prescription what the plan asks for next, or `null` off plan.
         * @param previousSet the last set logged against this exercise in this session, if any.
         * @param lastPerformed what the phone says was done last time this exercise was trained.
         * @param side the side the guided session is offering, which the lifter may then move.
         */
        fun of(
            unit: WeightUnit,
            prescription: PrescribedSet? = null,
            previousSet: DraftSet? = null,
            lastPerformed: LastPerformed? = null,
            side: LogSide? = null,
        ): SetEntrySeed {
            val isFirstSetOfTheExercise = previousSet == null

            // Most sets repeat the one before, so start there rather than at a default.
            val repeated: Double? = previousSet?.weightKg?.takeIf { it > 0.0 }?.let { rounded(it, unit) }
            val previousReps: Int? = previousSet?.reps?.takeIf { it >= 1 }

            if (prescription == null) {
                // Off plan. A new exercise starts at a loaded bar, which is where the Apple watch
                // starts; a later set repeats the one before it.
                return SetEntrySeed(
                    weightKg = if (isFirstSetOfTheExercise) {
                        unit.toKilograms(
                            if (unit == WeightUnit.POUNDS) FREE_ENTRY_POUNDS else FREE_ENTRY_KILOGRAMS
                        )
                    } else repeated,
                    reps = previousReps ?: DEFAULT_REPS,
                    rpe = if (isFirstSetOfTheExercise) DEFAULT_RPE else previousSet?.rpe,
                    side = side,
                )
            }

            // On plan the prescription wins, field by field, because every one of its fields is
            // optional. What it leaves blank falls back to the set before; and only on the first set
            // of the exercise to what the phone says was done last time, because after that the set
            // before is the better answer.
            return SetEntrySeed(
                weightKg = prescription.weightKg
                    ?: repeated
                    ?: lastPerformed?.weightKg
                        ?.takeIf { isFirstSetOfTheExercise && it > 0.0 }
                        ?.let { rounded(it, unit) },
                reps = (
                    prescription.reps
                        ?: previousReps
                        ?: lastPerformed?.reps?.takeIf { isFirstSetOfTheExercise && it >= 1 }
                        ?: DEFAULT_REPS
                    ).coerceAtLeast(1),
                rpe = prescription.rpe
                    ?: previousSet?.rpe
                    ?: lastPerformed?.rpe?.takeIf { isFirstSetOfTheExercise },
                side = side,
            )
        }

        /** A borrowed weight lands on the step a bar actually loads to, in the lifter's own unit. */
        private fun rounded(kilograms: Double, unit: WeightUnit): Double {
            val step = step(unit)
            val shown = Math.round(unit.fromKilograms(kilograms) / step) * step
            return unit.toKilograms(maxOf(step, shown))
        }
    }
}
