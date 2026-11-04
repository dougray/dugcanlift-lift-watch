package com.dugcanlift.liftkit

import kotlin.math.floor

/**
 * Heart rate per set: **the highest heart rate the watch measured around each set**, in whole bpm,
 * shown beside the set and never graded (the LIFT superproject's build plan, item 16).
 *
 * One pure function, with no sensor and no view in it, because this decides a number a lifter reads
 * about their own body and a rule inside a Health Services callback is a rule nobody can test. It is
 * the Kotlin half of a pair: LIFT for Apple Watch carries the same rule in Swift, and both are checked
 * against one fixture worked by hand, `fixtures/heart-rate-per-set.json`, which is never regenerated
 * from either port. If this file and that fixture disagree, this file is wrong.
 *
 * **A set has a tick and no start.** The watch logs and completes a set in one tap, and no build
 * records when a set began, so the window is drawn around the tick:
 *
 * - It **ends** [AFTER_TICK_SECONDS] after the tick, or at the next tick in the session if that comes
 *   sooner. Heart rate keeps rising for a few seconds after the bar is racked, and an optical sensor
 *   lags; twenty seconds catches that without reaching into the rest.
 * - It **starts** at the previous tick in the session, on whatever exercise, or at the session's start
 *   for the first set — but never more than [LOOKBACK_SECONDS] before the tick, or the set's own
 *   duration plus [TIMED_SET_ALLOWANCE_SECONDS] when a timed set states a longer one. So a walk to the
 *   gym before the first set does not hand that set a figure.
 * - **Both ends are inclusive.** A reading of zero or below is not a reading.
 *
 * **Not an average**: with no start time, an average over the window is mostly rest, so it would
 * describe the rest rather than the set. **Not recovery**: that needs the rest's end, which nothing
 * records. In a superset the two windows meet, so a figure there says less about which set raised it;
 * that is stated, not corrected for.
 *
 * A window with no reading has **no figure**: never a zero, never a neighbor's number, never
 * interpolated.
 */
object HeartRatePerSet {
    /** How long after its tick a set's window stays open, unless the next tick closes it first. */
    const val AFTER_TICK_SECONDS = 20.0

    /** How far before its tick a set's window may reach, at most, for a set with no longer duration. */
    const val LOOKBACK_SECONDS = 300.0

    /** What a timed set's window may reach back beyond its own stated duration. */
    const val TIMED_SET_ALLOWANCE_SECONDS = 30.0

    /** One heart-rate reading: when, in seconds, and the bpm the sensor reported. */
    data class Sample(val atSeconds: Double, val bpm: Double)

    /**
     * One set, as the rule sees it: its id, its tick in seconds (`null` for a set never ticked, which
     * gets no figure and is nobody's neighbor), and the duration a timed set states, if any.
     */
    data class Tick(val id: String, val atSeconds: Double?, val durationSeconds: Double? = null)

    /** Where a set's window runs, both ends inclusive. */
    data class Window(val start: Double, val end: Double) {
        operator fun contains(atSeconds: Double): Boolean = atSeconds >= start && atSeconds <= end
    }

    /**
     * Every ticked set's window. Sets are taken **in tick order**, whatever order they are listed in
     * and whatever exercise they belong to; a set with no tick has no window and does not cut anyone
     * else's. Two sets ticked in the same second keep the order they are listed in.
     */
    fun windows(sessionStart: Double, sets: List<Tick>): Map<String, Window> {
        val ticked = sets.filter { it.atSeconds != null }.sortedBy { it.atSeconds!! }
        val out = LinkedHashMap<String, Window>()
        ticked.forEachIndexed { i, set ->
            val tick = set.atSeconds!!
            val previous = ticked.getOrNull(i - 1)?.atSeconds ?: sessionStart
            val next = ticked.getOrNull(i + 1)?.atSeconds
            val lookback = maxOf(LOOKBACK_SECONDS, (set.durationSeconds ?: 0.0) + TIMED_SET_ALLOWANCE_SECONDS)
            val end = minOf(tick + AFTER_TICK_SECONDS, next ?: Double.POSITIVE_INFINITY)
            out[set.id] = Window(start = maxOf(previous, tick - lookback), end = end)
        }
        return out
    }

    /** The highest reading inside [window], rounded to a whole bpm; `null` when there is none. */
    fun highest(window: Window, samples: List<Sample>): Int? =
        samples.filter { isReading(it.bpm) && it.atSeconds in window }.maxOfOrNull { it.bpm }?.let(::wholeBpm)

    /**
     * The figure for every set, by id: the highest reading in its window, or `null` — for a window
     * with no reading and for a set never ticked alike. Samples need not be sorted.
     */
    fun figures(sessionStart: Double, samples: List<Sample>, sets: List<Tick>): Map<String, Int?> {
        val windows = windows(sessionStart, sets)
        return sets.associate { set -> set.id to windows[set.id]?.let { highest(it, samples) } }
    }

    /** A reading is a finite bpm above zero. A zero is a sensor saying it has nothing. */
    fun isReading(bpm: Double): Boolean = bpm.isFinite() && bpm > 0.0

    /** To the nearest whole bpm, **halves away from zero**: 141.5 is 142. Not `kotlin.math.round`,
     *  which rounds halves to even and would make 142.5 a 142. */
    fun wholeBpm(bpm: Double): Int =
        if (bpm >= 0.0) floor(bpm + 0.5).toInt() else -floor(-bpm + 0.5).toInt()
}
