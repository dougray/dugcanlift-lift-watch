package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PlanExercise
import com.dugcanlift.liftkit.link.PrescribedSet

/**
 * One set to actually perform: the prescription it answers, and the side it is done on (`null` for
 * an ordinary two-sided set).
 */
data class PlannedSet(val prescription: PrescribedSet, val side: LogSide? = null)

/**
 * The sets to actually perform, in order, one entry per set: an each-side set expands to two — left
 * then right — a set that names a side is that side once **even when the exercise is each side**,
 * and everything else is one two-sided set.
 *
 * This is LIFT Android's `PerSideLogging.prescribedOrder` with the two-sided sets kept in, because
 * this list is "sets to perform" and that one is "sides to walk". Dropping the nulls from this gives
 * that list back exactly, which is what [GuidedSession] does.
 */
val PlanExercise.plannedSets: List<PlannedSet>
    get() = sets.flatMap { set ->
        when {
            set.side != null -> listOf(PlannedSet(set, set.side))
            eachSide -> listOf(PlannedSet(set, LogSide.LEFT), PlannedSet(set, LogSide.RIGHT))
            else -> listOf(PlannedSet(set, null))
        }
    }

/** Whether this exercise says anything about sides at all. LIFT Android's `prescribesSides`. */
val PlanExercise.prescribesSides: Boolean get() = eachSide || sets.any { it.side != null }

/**
 * Where the lifter is inside a pushed plan: which exercise, which set of it, which side that set is
 * for, and what to do when a set is logged.
 *
 * A value type with no Android in it, for the reason [RestTimer] is one: this decides what the
 * session screen says and when the next exercise starts, and a rule living in a Compose `remember`
 * cannot be tested. `GuidedSessionTest` pins it.
 *
 * It tracks **position, never the sets themselves**. The sets logged against a plan are ordinary
 * [DraftSet]s on an ordinary [WorkoutDraft], so nothing downstream — the revision, the outbox, the
 * `SESSION_FINISHED` that travels home — knows a plan existed.
 *
 * **Sides.** A coach can say an exercise is each side, or name a side on one set (PLAN-FORMAT.md
 * "Sides"). An each-side exercise is then twice the sets — "3 x 8 each side" reads `3/6`, not `3/3`
 * — and each one is offered on a side. *Which* side is **LIFT Android's own rule, ported function
 * for function** from `PerSideLogging.nextPrescribedSide` and `PerSideLogging.defaultSide`, composed
 * as `startingSide` does: the side of the first prescribed sided set the log has not filled, and
 * past the prescription whichever side has fewer sets logged, left breaking a tie. A plan that says
 * nothing about sides behaves exactly as it always has.
 *
 * Not itself serializable: [Plan] lives in the shared `link` package, which deliberately imports no
 * JSON library at all (`LinkProtocol`'s file comment), and annotating it there would break the one
 * rule that keeps the two repos' copies identical. [SessionStore] persists a session by keeping the
 * plan **as the wire delivered it** and running it back through `LinkPayloads.decodePlan`.
 */
data class GuidedSession(
    val plan: Plan,
    /**
     * The sides of the sets logged against each exercise, in the order they were logged, parallel to
     * `plan.exercises`. `null` is a two-sided set, which is every set of a plan with no sides.
     *
     * The sides rather than a count, because the side to offer next is read from what was actually
     * logged, not from what was prescribed: the prescription suggests and the log records, on the
     * wrist exactly as on the phone.
     */
    val loggedSides: List<List<LogSide?>>,
    val exerciseIndex: Int,
) {
    /** Sets completed per exercise, parallel to `plan.exercises`. */
    val completedSets: List<Int> get() = loggedSides.map { it.size }

    /**
     * The index of the exercise being trained, or `null` once the plan is done — so a caller mapping
     * position onto its own list cannot read [exerciseIndex] past the end of a finished session.
     */
    val currentExerciseIndex: Int?
        get() = if (!isComplete && exerciseIndex in plan.exercises.indices) exerciseIndex else null

    val currentExercise: PlanExercise? get() = currentExerciseIndex?.let { plan.exercises[it] }

    /**
     * 0-based index of the set about to be performed, within the current exercise's [plannedSets] —
     * the sets to *perform*, so an each-side exercise's three prescribed rows are six positions.
     * Clamped to the last one once the exercise is finished, so a caller reading a prescription
     * after the final set gets the final set's.
     */
    val currentSetIndex: Int
        get() {
            val exercise = currentExercise ?: return 0
            return minOf(loggedSides[exerciseIndex].size, maxOf(0, exercise.plannedSets.size - 1))
        }

    /** 1-based, for "3/5". */
    val currentSetNumber: Int
        get() {
            val exercise = currentExercise ?: return 0
            return minOf(loggedSides[exerciseIndex].size + 1, exercise.plannedSets.size)
        }

    val currentSetCount: Int get() = currentExercise?.plannedSets?.size ?: 0

    /**
     * "3/5" as the design spec draws it, and "3/6 · L" once a side is in play — the position and the
     * side the next set is for in one line, because together they are the one answer to "what am I
     * doing now".
     */
    val positionText: String
        get() {
            if (currentExercise == null) return ""
            val position = "$currentSetNumber/$currentSetCount"
            val side = currentSide ?: return position
            return "$position · ${side.shortLabel}"
        }

    /**
     * The side the next set is to be performed on, or `null` when this exercise says nothing about
     * sides.
     *
     * LIFT Android's `startingSide`: `nextPrescribedSide(...) ?: defaultSide(...)`. The fallback is
     * only reached for an each-side exercise, because that is the one the lifter is doing a limb at
     * a time throughout — a bench press with one right-side set in it stops asking about sides the
     * moment that set is logged, which is the phone's `pendingNamedSide` exactly, with `eachSide`
     * standing in for the lifter's own per-side preference (a phone screen the watch does not have).
     */
    val currentSide: LogSide?
        get() = currentExercise?.let { side(it, loggedSides[exerciseIndex]) }

    /**
     * The prescription the next set answers — LIFT Android's `prescribedSetFor`. With sides in play
     * that is the next unfilled prescribed set *on that side*, so three lefts logged first still
     * leave the first right's numbers for the first right set.
     */
    val currentPrescription: PrescribedSet?
        get() {
            val exercise = currentExercise ?: return null
            val planned = exercise.plannedSets
            val side = currentSide
                ?: return planned.getOrNull(currentSetIndex)?.prescription
            val onSide = planned.filter { it.side == side }
            val done = loggedSides[exerciseIndex].count { it == side }
            // Past what the coach asked for on this side: the last thing they did ask for there,
            // which is what the lifter is repeating.
            return onSide.getOrNull(done)?.prescription
                ?: onSide.lastOrNull()?.prescription
                ?: planned.lastOrNull()?.prescription
        }

    val isComplete: Boolean get() = nextUnfinished(0, plan, loggedSides) == null

    /** Sets done across the whole plan, for a progress line. */
    val completedSetCount: Int get() = loggedSides.sumOf { it.size }

    fun completedSets(exerciseIndex: Int): Int = loggedSides.getOrNull(exerciseIndex)?.size ?: 0

    /** What [recordSet] produced: where the session now is, and the prescription just performed. */
    data class Recorded(val session: GuidedSession, val performed: PrescribedSet?)

    /**
     * Records one completed set against the current exercise and advances when that exercise has had
     * all of its, returning the prescription that **was just performed** — which is where the rest
     * interval comes from, not the next set's and not the next exercise's.
     *
     * [side] is the side the set was actually logged on, which the caller takes from the lifter's
     * own choice — [currentSide] is only what that choice starts at. A set recorded with no side is
     * a two-sided set, and that is every set of a plan with no sides.
     *
     * Logging more sets than an exercise asked for is not an error: the count keeps rising and the
     * extra set is on the draft like any other. **Once the whole plan is complete this records
     * nothing** — there is no position left to be at — and the set still reaches the draft, which is
     * what travels home. A plan is a prescription, not a limit; this only ever tracked position
     * inside it.
     */
    fun recordSet(side: LogSide? = null): Recorded {
        if (currentExercise == null) return Recorded(this, null)
        val performed = currentPrescription
        val updated = loggedSides.toMutableList()
        updated[exerciseIndex] = updated[exerciseIndex] + side
        val next = nextUnfinished(exerciseIndex, plan, updated) ?: exerciseIndex
        return Recorded(copy(loggedSides = updated, exerciseIndex = next), performed)
    }

    /**
     * Jumps to an exercise the lifter picked out of order. Out of range is a caller bug, not a
     * session change, so it is ignored rather than thrown.
     */
    fun select(index: Int): GuidedSession =
        if (index in plan.exercises.indices) copy(exerciseIndex = index) else this

    companion object {
        fun start(plan: Plan): GuidedSession {
            val empty = List(plan.exercises.size) { emptyList<LogSide?>() }
            // An exercise prescribing no sets at all is already done, so the session opens on the
            // first one that actually asks for something.
            return GuidedSession(plan, empty, nextUnfinished(0, plan, empty) ?: 0)
        }

        /**
         * The next exercise with sets still owed, searching from [start] forwards and then wrapping
         * to the beginning — wrapping so that skipping an exercise and coming back to it later works
         * without a separate "unfinished" screen. `null` when the whole plan is done.
         *
         * Owed is measured in sets to *perform*: an each-side exercise is not done until both sides
         * of every prescribed row are.
         */
        private fun nextUnfinished(start: Int, plan: Plan, logged: List<List<LogSide?>>): Int? {
            val count = plan.exercises.size
            if (count == 0) return null
            for (offset in 0 until count) {
                val index = (start + offset) % count
                val done = logged.getOrNull(index) ?: continue
                if (done.size < plan.exercises[index].plannedSets.size) return index
            }
            return null
        }

        /**
         * LIFT Android's rule, function for function (`PerSideLogging.nextPrescribedSide` then
         * `defaultSide`): walk the prescribed sided sets in the order the coach wrote them and stop
         * at the first one the log has not filled on that side; when every one is filled, offer
         * whichever side has fewer logged, **left breaking a tie** (the phone's strict `<` on right
         * against left).
         *
         * Two-sided sets contribute nothing to the walk on either side — `compactMap` here is the
         * phone's `prescribedOrder` dropping them.
         */
        internal fun side(exercise: PlanExercise, logged: List<LogSide?>): LogSide? {
            val order = exercise.plannedSets.mapNotNull { it.side }
            if (order.isEmpty()) return null

            val have = mutableMapOf(LogSide.LEFT to 0, LogSide.RIGHT to 0)
            for (side in logged.filterNotNull()) have[side] = have.getValue(side) + 1

            val used = mutableMapOf(LogSide.LEFT to 0, LogSide.RIGHT to 0)
            for (side in order) {
                if (used.getValue(side) >= have.getValue(side)) return side
                used[side] = used.getValue(side) + 1
            }
            if (!exercise.eachSide) return null
            return if (have.getValue(LogSide.RIGHT) < have.getValue(LogSide.LEFT)) LogSide.RIGHT else LogSide.LEFT
        }
    }
}
