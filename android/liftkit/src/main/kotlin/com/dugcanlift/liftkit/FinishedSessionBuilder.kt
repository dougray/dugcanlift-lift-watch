package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.FinishedExercise
import com.dugcanlift.liftkit.link.FinishedSession
import com.dugcanlift.liftkit.link.LoggedSet
import com.dugcanlift.liftkit.link.LoggedSetReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turning the watch's own draft into what travels home over LIFT Link.
 *
 * Here rather than beside the wire types in `link` because it reads [WorkoutDraft], the watch's own
 * domain, which the phone does not compile — and out of the Compose layer because this is the rule
 * that decides whether a lifter's work reaches their phone intact, and a rule in a state holder's
 * method body is a rule nobody can test without a radio.
 */

/** Local calendar day, `yyyy-MM-dd`. Local, as every day key in LIFT is. */
fun dayKey(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate())

/**
 * This workout as the phone will store it.
 *
 * Everything the draft holds travels: every exercise in order, every set with its weight, reps, RPE
 * and **side**. Nothing is filtered.
 *
 * `day` is the local day the session **started**: a set logged at 00:10 belongs to the evening it
 * began, which is how the phone's own day-of-record works, and taking it from the finish time would
 * file a late session under tomorrow.
 *
 * `name` falls back to "Workout" only when the draft has none at all — the phone names a blank day
 * from this, and an empty name would leave the day unnamed rather than wrong.
 *
 * **A weight of zero is sent as absent.** A set of six reps with no weight is what Doug's own
 * routine prescribes, and `weightKg = 0.0` on the wire would land in the phone's log as a lift of
 * nothing rather than a lift nobody weighed. [LoggedSet.weightKg] is nullable precisely so that
 * distinction survives, and reps are always present because a logged set has them.
 */
fun WorkoutDraft.toFinishedSession(zone: ZoneId = ZoneId.systemDefault()): FinishedSession =
    FinishedSession(
        sessionId = id,
        revision = revision,
        day = dayKey(startedAtEpochSeconds, zone),
        name = name.trim().ifEmpty { "Workout" },
        startedAtEpochSeconds = startedAtEpochSeconds,
        exercises = exercises.sortedBy { it.orderIndex }.map { exercise ->
            FinishedExercise(
                name = exercise.name,
                // "" is not an equipment: the key is omitted, so the receiver matches this lift's
                // identity the same way it matches one of its own.
                equipment = exercise.equipment?.takeIf { it.isNotBlank() },
                sets = exercise.sets.sortedBy { it.orderIndex }.map { it.toLoggedSet() },
            )
        },
    )

/** One set, for the optional streaming report a phone on the bench reads. */
fun DraftSet.toLoggedSet(): LoggedSet = LoggedSet(
    weightKg = weightKg.takeIf { it > 0.0 },
    reps = reps.takeIf { it >= 1 },
    rpe = rpe,
    side = side,
)

/**
 * The `SET_LOGGED` report for the set just added to [exerciseId].
 *
 * `setIndex` is the set's position within that exercise, so the phone recognises a repeat rather
 * than appending one. `null` when the exercise or its last set is not there — a caller bug, and
 * nothing is worth sending for it.
 */
fun WorkoutDraft.lastSetReport(exerciseId: String): LoggedSetReport? {
    val exercise = exercise(exerciseId) ?: return null
    val set = exercise.sets.lastOrNull() ?: return null
    return LoggedSetReport(
        sessionId = id,
        revision = revision,
        exerciseName = exercise.name,
        equipment = exercise.equipment?.takeIf { it.isNotBlank() },
        setIndex = set.orderIndex,
        set = set.toLoggedSet(),
        loggedAtEpochSeconds = set.completedAtEpochSeconds ?: updatedAtEpochSeconds,
    )
}
