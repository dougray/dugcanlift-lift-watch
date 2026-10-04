package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LastPerformed
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PlanSource
import com.dugcanlift.liftkit.link.PrescribedSet

/**
 * Formatting for a plan and for a logged set — a port of LIFT for Apple Watch's `PlanDisplay.swift`,
 * **including the spelling of the multiplication sign**: the Apple watch writes an ASCII `x`, not a
 * `×`, so "185 x 5" is what a lifter reads on either wrist.
 *
 * Here rather than in a composable because the rules are the point. "Blank must never render as
 * zero" is what the whole presence-bit wire exists to protect (`LinkModels`' file comment), and a
 * rule inside a `@Composable` is a rule nobody can test.
 */

/** The one character the position line, a prescription and a set row all use: "30 x 8 · L". */
val LogSide.shortLabel: String get() = if (this == LogSide.LEFT) "L" else "R"

val LogSide.opposite: LogSide get() = if (this == LogSide.LEFT) LogSide.RIGHT else LogSide.LEFT

val PlanSource.displayName: String
    get() = when (this) {
        PlanSource.ROUTINE -> "Routine"
        PlanSource.COACH_PLAN -> "From your coach"
    }

/** "Deadlift (Barbell)" — the same convention [DraftExercise.displayName] uses. */
val PlanExercise.displayName: String
    get() = equipment?.takeIf { it.isNotBlank() }
        ?.let { "$name (${it.replaceFirstChar { c -> c.uppercase() }})" } ?: name

/**
 * Whether this is a plan for [day] (`yyyy-MM-dd`, local). A plan with no `scheduledFor` is not tied
 * to a day and always is.
 *
 * The phone sends a plan when there is one and nothing when there is not, so without this a plan
 * received on Monday is still offered as "Today" on Tuesday — and on Wear that lasts until the app
 * is opened again with the phone in range, which may be days.
 */
fun Plan.isScheduledFor(day: String): Boolean = scheduledFor == null || scheduledFor == day

/**
 * Sets to actually perform across the whole plan, which is not the same as prescribed rows: an
 * each-side exercise's "3 x 8" is six sets.
 */
val Plan.totalSetCount: Int get() = exercises.sumOf { it.plannedSets.size }

object PlanFormat {
    /** Trailing ".0" is noise on a wrist; a real half-kilo is not. */
    fun weight(kilograms: Double, unit: WeightUnit): String = trimmed(unit.fromKilograms(kilograms))

    fun rpe(value: Double): String = trimmed(value)

    private fun trimmed(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.rint(rounded)) rounded.toInt().toString() else "%.1f".format(rounded)
    }
}

/** The weight alone — "185" — or `null` when none is prescribed. */
fun PrescribedSet.weightText(unit: WeightUnit): String? =
    weightKg?.let { PlanFormat.weight(it, unit) }

/**
 * The big line on the session screen: "185 x 5", "5 reps", "185 lb", or "—" when this set
 * prescribes nothing at all. **Never "0 x 5"** — Doug's own routine is four sets of six reps with
 * no weight, and a zero there would be the watch inventing a number the coach did not write.
 *
 * [side] is the side the set is about to be done on, which the caller reads from the session rather
 * than from the set: an each-side exercise's sets name no side of their own and are still done one
 * limb at a time. It is appended as "30 x 8 · L".
 */
fun PrescribedSet.headline(unit: WeightUnit, side: LogSide? = null): String {
    val weight = weightText(unit)
    var text = when {
        weight != null && reps != null -> "$weight x $reps"
        weight != null -> "$weight ${unit.abbreviation}"
        reps != null -> "$reps reps"
        else -> "—"
    }
    if (side != null) text += " · ${side.shortLabel}"
    return text
}

/** Everything the set prescribes, RPE included: "185 x 5 @8", and "30 x 8 @8 · L" with a side. */
fun PrescribedSet.summary(unit: WeightUnit, side: LogSide? = null): String {
    var text = headline(unit)
    if (rpe != null) text += " @${PlanFormat.rpe(rpe)}"
    if (side != null) text += " · ${side.shortLabel}"
    return text
}

/** Whether this set prescribes no numbers at all. A side is not a number. */
val PrescribedSet.isEmpty: Boolean get() = weightKg == null && reps == null && rpe == null

/**
 * "185x5 @8" — the reference line under the prescription. `null` when the record holds no numbers
 * worth showing, so the caller shows nothing at all rather than a dash pretending to be history.
 */
fun LastPerformed.summary(unit: WeightUnit): String? {
    var text = when {
        weightKg != null && reps != null -> "${PlanFormat.weight(weightKg, unit)}x$reps"
        weightKg != null -> "${PlanFormat.weight(weightKg, unit)} ${unit.abbreviation}"
        reps != null -> "$reps reps"
        else -> return null
    }
    if (rpe != null) text += " @${PlanFormat.rpe(rpe)}"
    return text
}

/** "325 x 5 @7.5", and "30 x 8 @8 · L" for a set done on one side. */
fun DraftSet.display(unit: WeightUnit): String {
    var text = "${PlanFormat.weight(weightKg, unit)} x $reps"
    if (rpe != null) text += " @${PlanFormat.rpe(rpe)}"
    side?.let { text += " · ${it.shortLabel}" }
    return text
}
