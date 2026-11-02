package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.ExerciseGroups
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.SetKind

/**
 * The words for a coach's set types on the wrist (PLAN-FORMAT.md "Set types"): "Superset A" for two
 * exercises done round by round, "Circuit A" for three or more, "A1", "A2" before each member, and
 * "drop" / "rest-pause" after a set.
 *
 * **Ported from LIFT Android's `SetTypes` (`letters`, `labels`, `heading`, `suffix`) and
 * `SetKind.label`, function for function** — which is itself Coach web's `prescriptions.js` again —
 * so a coach, their client's phone and their client's wrist read the same words. Change them in
 * every build or in none; never improve them here.
 *
 * Here rather than in a composable for the reason [PlanFormat] is: the rules are the point, and a
 * rule inside a `@Composable` is a rule nobody can test. `SetTypeLabelsTest` pins it.
 *
 * Shown, never graded: nothing here or on any screen says whether a superset was done as one.
 */
object SetTypeLabels {
    /**
     * What an exercise in a group is labelled. [group] is "Superset A" for two, "Circuit A" for three
     * or more, said once above the group's [first] member; [member] is "A1", "A2". LIFT Android's
     * `SetTypes.GroupLabel`, field for field.
     */
    data class GroupLabel(val group: String, val member: String, val first: Boolean, val size: Int) {
        /** "Superset A · A1": the group and the place in it on one line, the shape a wrist has room for. */
        val line: String get() = "$group · $member"
    }

    /** Spreadsheet letters: 0 is A, 25 Z, 26 AA. A day with 27 groups is not likely, but a label must never be blank. */
    fun letters(n: Int): String {
        val out = StringBuilder()
        var x = n + 1
        while (x > 0) {
            out.insert(0, ('A' + (x - 1) % 26))
            x = (x - 1) / 26
        }
        return out.toString()
    }

    /**
     * Each exercise's label, or null when it is not grouped. Groups are [ExerciseGroups.runs] — a
     * lone number is no group — the letters run A, B, C... within the day, and members are numbered
     * from 1.
     */
    fun labels(groups: List<Int?>): List<GroupLabel?> {
        val runs = ExerciseGroups.runs(groups)
        val sizes = runs.filterNotNull().groupingBy { it }.eachCount()
        val seen = mutableMapOf<Int, Int>()
        return runs.map { g ->
            if (g == null) return@map null
            val letter = letters(g - 1)
            val n = (seen[g] ?: 0) + 1
            seen[g] = n
            val size = sizes.getValue(g)
            GroupLabel(
                group = (if (size >= 3) "Circuit " else "Superset ") + letter,
                member = "$letter$n",
                first = n == 1,
                size = size,
            )
        }
    }

    /** An exercise's heading with its place in a group: "A1 · Dumbbell Bench Press (Dumbbell)". */
    fun heading(title: String, label: GroupLabel?): String =
        if (label == null) title else "${label.member} · $title"

    /** " drop", " rest-pause", or nothing: said after a set's side. */
    fun suffix(kind: SetKind?): String = kind?.let { " ${it.label}" } ?: ""
}

/** The word a set's text ends with: "30 x 8 · L drop". LIFT Android's `SetKind.label`. */
val SetKind.label: String
    get() = when (this) {
        SetKind.DROP -> "drop"
        SetKind.REST_PAUSE -> "rest-pause"
    }

/** The same word standing on a line of its own, under the prescription: "Drop", "Rest-pause". */
val SetKind.title: String get() = label.replaceFirstChar { it.uppercase() }

/** Each exercise's group label, parallel to `exercises`. */
val Plan.groupLabels: List<SetTypeLabels.GroupLabel?>
    get() = SetTypeLabels.labels(exercises.map { it.group })
