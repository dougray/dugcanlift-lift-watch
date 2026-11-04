package com.dugcanlift.liftkit

import kotlinx.serialization.Serializable

/** A session's average and highest heart rate, in whole bpm. Never zero: no reading is no pair. */
data class SessionHeartRate(val averageBpm: Int, val maxBpm: Int)

/**
 * Heart rate around each set, live: the readings of the last few minutes, held **in memory only**,
 * and the figure for each set worked out once its window has closed.
 *
 * [HeartRatePerSet] is the rule; this is the bookkeeping that feeds it during a session, kept here
 * rather than in the Health Services callback so it is tested on the JVM. It is not thread-safe: the
 * watch's `SessionController` owns it and calls it from the main thread, as it does every edit.
 *
 * **When a window closes.** By the rule a window ends 20 seconds after its tick or at the next tick.
 * The figure is settled once a reading timed **after** that end has arrived — readings arrive in
 * order, so every reading inside the window is in hand by then — or when the session finishes, for
 * any window still open. Settling on a clock instead would miss readings Health Services batches up
 * while the screen is off, which is most of a rest.
 *
 * **What it keeps.** Readings from the last [KEEP_SECONDS], which is more than any open window can
 * reach back (a window starts at most five minutes before its tick and closes at most 20 s after
 * it), and the running total behind the session's average and maximum. The samples are never
 * written to disk, never sent and never kept past the session.
 *
 * **What survives a process death is the running total, never a reading.** Wear kills the app between
 * sets, and the session comes back from disk; [totals] rides in its `SessionSnapshot` so the
 * session's average and maximum still cover the readings from before, and [resume] takes them back.
 * The readings themselves are gone, so a set ticked before the restart gets no figure afterwards: its
 * window would be read from half its readings, and a figure from half a window is a number nobody
 * measured. Sets ticked after the restart are worked out as usual.
 */
class SetHeartRates {
    private val samples = ArrayList<HeartRatePerSet.Sample>()

    /** Sets whose window has been worked out, with or without a figure. A window is worked out once. */
    private val settled = HashSet<String>()

    private var count = 0
    private var total = 0.0
    private var highest = 0.0

    /** When the newest reading was taken, or `null` before the first. */
    var newestSampleSeconds: Double? = null
        private set

    /** Readings held right now. For tests: the bound is the point. */
    val heldSampleCount: Int get() = samples.size

    /** The count, sum and highest reading behind [session], for the session on disk. */
    @Serializable
    data class Totals(val count: Int, val total: Double, val highest: Double)

    /** What [session] is worked out from, or `null` before the first reading. */
    val totals: Totals? get() = if (count == 0) null else Totals(count, total, highest)

    /**
     * A session restored from disk: its [saved] totals back, no readings, and every set [draft] has
     * already ticked settled as it stands -- a set with a figure keeps it, and one without gets none.
     * Totals that make no sense (a negative count, a non-finite sum) are dropped rather than trusted:
     * the session then reads from the readings after the restart, which is all it can honestly say.
     */
    fun resume(saved: Totals?, draft: WorkoutDraft) {
        reset()
        if (saved != null && saved.count > 0 && saved.total.isFinite() && saved.total > 0.0 &&
            saved.highest.isFinite() && saved.highest > 0.0
        ) {
            count = saved.count
            total = saved.total
            highest = saved.highest
        }
        draft.exercises.flatMap { it.sets }
            .filter { it.completedAtEpochSeconds != null }
            .forEach { settled += it.id }
    }

    /** A new session: nothing from the last one carries over. */
    fun reset() {
        samples.clear()
        settled.clear()
        count = 0
        total = 0.0
        highest = 0.0
        newestSampleSeconds = null
    }

    /** One reading. A zero, a negative or a non-finite bpm is not a reading and is not kept. */
    fun record(atSeconds: Double, bpm: Double) {
        if (!HeartRatePerSet.isReading(bpm) || !atSeconds.isFinite()) return
        samples += HeartRatePerSet.Sample(atSeconds, bpm)
        count++
        total += bpm
        if (bpm > highest) highest = bpm
        newestSampleSeconds = maxOf(newestSampleSeconds ?: atSeconds, atSeconds)
    }

    /**
     * The session's average and maximum over every reading since [reset], or `null` when there was
     * none — what a simulator, a refused permission and a watch on a bench all produce, and none of
     * them is a heart rate of zero. Rounded as a set's figure is, so the session's maximum is never
     * below the highest set's.
     */
    val session: SessionHeartRate?
        get() = if (count == 0 || highest <= 0.0) null
        else SessionHeartRate(HeartRatePerSet.wholeBpm(total / count), HeartRatePerSet.wholeBpm(highest))

    /**
     * [draft] with a figure on every set whose window has closed since the last call, then the
     * readings no open window can reach dropped. [finishing] closes every window still open, with the
     * readings there are.
     *
     * A set that already has a figure keeps it, and a set whose window held no reading gets none and
     * is not looked at again. A set deleted meanwhile is simply not in the draft any more.
     */
    fun settle(draft: WorkoutDraft, nowEpochSeconds: Long, finishing: Boolean = false): WorkoutDraft {
        val sets = draft.exercises.flatMap { it.sets }
        sets.filter { it.maxBpm != null }.forEach { settled += it.id }
        val windows = HeartRatePerSet.windows(
            sessionStart = draft.startedAtEpochSeconds.toDouble(),
            sets = sets.map { HeartRatePerSet.Tick(it.id, it.completedAtEpochSeconds?.toDouble()) },
        )
        val newest = newestSampleSeconds
        val figures = HashMap<String, Int>()
        windows.forEach { (id, window) ->
            if (id in settled) return@forEach
            if (!finishing && (newest == null || newest <= window.end)) return@forEach
            settled += id
            HeartRatePerSet.highest(window, samples)?.let { figures[id] = it }
        }
        newest?.let { latest -> samples.removeAll { it.atSeconds < latest - KEEP_SECONDS } }
        return draft.withHeartRates(figures, nowEpochSeconds)
    }

    companion object {
        /** Six minutes: past the longest reach of any window that can still be open. */
        const val KEEP_SECONDS = 360.0
    }
}

/**
 * The words for heart rate on the wrist: "max 142 bpm" beside a set and "Heart rate: average 118 ·
 * max 156 bpm" for the session. **The same words in every build** — LIFT for Apple Watch, LIFT
 * Android and LIFT iPhone say exactly these — or changed in all of them.
 *
 * Shown, never graded: no zone, no color, no threshold, no trend, no "recovered" or "ready", and no
 * percentage of a maximum heart rate, which would need an age-based guess the app is not qualified to
 * make. Nothing here says what a number means, and `HeartRateLabelsTest` checks that it does not.
 */
object HeartRateLabels {
    /** "max 142 bpm", or `null` for a set with no figure — which shows nothing at all. */
    fun set(maxBpm: Int?): String? = maxBpm?.takeIf { it > 0 }?.let { "max $it bpm" }

    /**
     * "Heart rate: average 118 · max 156 bpm", or `null` when neither was measured. One half alone
     * says only that half; the watch always works out both, so that is a phone reading a file.
     */
    fun session(averageBpm: Int?, maxBpm: Int?): String? {
        val parts = listOfNotNull(
            averageBpm?.takeIf { it > 0 }?.let { "average $it" },
            maxBpm?.takeIf { it > 0 }?.let { "max $it" },
        )
        return if (parts.isEmpty()) null else "Heart rate: ${parts.joinToString(" · ")} bpm"
    }

    fun session(heartRate: SessionHeartRate?): String? = session(heartRate?.averageBpm, heartRate?.maxBpm)
}
