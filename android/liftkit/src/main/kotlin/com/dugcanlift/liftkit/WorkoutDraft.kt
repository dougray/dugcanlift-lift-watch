package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import kotlinx.serialization.Serializable

/**
 * The workout the watch is building, with no phone in range.
 *
 * A port of LIFT for Apple Watch's `WorkoutDraft`, and a value type for the same reason: the watch
 * owns its copy and the phone reconciles by [revision] rather than by wall-clock time, which is not
 * trustworthy across two devices.
 *
 * **The plan is not the workout.** Training against a pushed plan builds one of these, exactly as
 * free entry does, so everything downstream of a logged set — the revision, the outbox, the
 * `SESSION_FINISHED` that travels home — never learns that a plan existed.
 *
 * Every mutation that actually changes something goes through `commit`, so there is exactly one
 * place the revision can advance. A rejected edit leaves it alone, or the phone would see a bump
 * with no change behind it and re-sync for nothing.
 *
 * Serializable because the watch's radio is off unless LIFT is open: a session interrupted by a
 * process death has to come back, and on Wear there is no `transferUserInfo` to re-deliver it.
 */
@Serializable
data class WorkoutDraft(
    val id: String,
    val name: String,
    val exercises: List<DraftExercise> = emptyList(),
    /** Starts at 1 and increases by one per accepted edit. Never decreases. */
    val revision: Int = 1,
    val startedAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val finishedAtEpochSeconds: Long? = null,
) {
    val isFinished: Boolean get() = finishedAtEpochSeconds != null

    /** Warm-up sets are deliberately excluded, matching the phone app. */
    val totalVolumeKg: Double get() = exercises.sumOf { it.volumeKg }

    val totalSetCount: Int get() = exercises.sumOf { it.sets.size }

    val completedSetCount: Int get() = exercises.sumOf { ex -> ex.sets.count { it.completedAtEpochSeconds != null } }

    fun exercise(exerciseId: String): DraftExercise? = exercises.firstOrNull { it.id == exerciseId }

    private fun commit(nowEpochSeconds: Long) = copy(revision = revision + 1, updatedAtEpochSeconds = nowEpochSeconds)

    fun addExercise(
        refId: String,
        name: String,
        equipment: String? = null,
        nowEpochSeconds: Long,
        idFactory: () -> String,
    ): WorkoutDraft = copy(
        exercises = exercises + DraftExercise(
            id = idFactory(), exerciseRefId = refId, name = name,
            orderIndex = exercises.size, equipment = equipment,
        )
    ).commit(nowEpochSeconds)

    /**
     * Appends a completed set. Unlike the Apple original this does not separate "append" from
     * "complete": the wrist has no screen on which a set is entered and left unfinished, and a
     * one-step append is one fewer state for a session that has to survive being killed.
     *
     * An unknown exercise is a caller bug, not a workout change, so nothing is committed and the
     * draft comes back unchanged.
     */
    fun logSet(
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        side: LogSide? = null,
        nowEpochSeconds: Long,
        idFactory: () -> String,
    ): WorkoutDraft {
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index < 0) return this
        val target = exercises[index]
        val set = DraftSet(
            id = idFactory(), orderIndex = target.sets.size, weightKg = weightKg, reps = reps,
            rpe = rpe, isWarmup = isWarmup, side = side, completedAtEpochSeconds = nowEpochSeconds,
        )
        val updated = exercises.toMutableList()
        updated[index] = target.copy(sets = target.sets + set)
        return copy(exercises = updated).commit(nowEpochSeconds)
    }

    /** Removes the last set of an exercise. The one edit a wrist needs: a set logged by a mis-tap. */
    fun removeLastSet(exerciseId: String, nowEpochSeconds: Long): WorkoutDraft {
        val index = exercises.indexOfFirst { it.id == exerciseId }
        if (index < 0 || exercises[index].sets.isEmpty()) return this
        val updated = exercises.toMutableList()
        updated[index] = exercises[index].let { it.copy(sets = it.sets.dropLast(1)) }
        return copy(exercises = updated).commit(nowEpochSeconds)
    }

    fun finished(atEpochSeconds: Long): WorkoutDraft =
        if (finishedAtEpochSeconds != null) this
        else copy(finishedAtEpochSeconds = atEpochSeconds).commit(atEpochSeconds)
}

@Serializable
data class DraftExercise(
    val id: String,
    /**
     * Reference-data id. [name] and [equipment] are snapshots, so history does not change when the
     * exercise database does. A plan carries no exercise ids (PLAN-FORMAT gives workouts none), so
     * for a planned exercise this is its position: `plan:0`.
     */
    val exerciseRefId: String,
    val name: String,
    val orderIndex: Int,
    val equipment: String? = null,
    val sets: List<DraftSet> = emptyList(),
) {
    /** "Deadlift (Barbell)" — equipment in parentheses, as on the phone. */
    val displayName: String get() = equipment?.takeIf { it.isNotBlank() }
        ?.let { "$name (${it.replaceFirstChar { c -> c.uppercase() }})" } ?: name

    val volumeKg: Double get() = sets.sumOf { it.volumeKg }
}

@Serializable
data class DraftSet(
    val id: String,
    val orderIndex: Int,
    val weightKg: Double = 0.0,
    val reps: Int = 0,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    /**
     * Which limb this set was done on. **Absent is "both", forever** — the phone's own rule for
     * `sideRaw`, and a set written before any of this decodes with no side and stays two-sided.
     *
     * Stored as [LogSide.code] by [LogSideAsCode] rather than by name: `link` carries no
     * serialization annotations by design, and the code is the number the wire already uses.
     */
    @Serializable(with = LogSideAsCode::class) val side: LogSide? = null,
    val completedAtEpochSeconds: Long? = null,
) {
    val volumeKg: Double get() = if (isWarmup) 0.0 else reps * weightKg
}
