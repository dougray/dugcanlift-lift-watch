package com.dugcanlift.liftwear

import com.dugcanlift.liftkit.HeartRatePerSet

/**
 * The timing rules of heart rate during a run, as values with no clock, radio or Health Services in
 * them, for the reason `MacroFields` is one on the Coach side: a rule that lives inside a service's
 * callbacks cannot be tested, and these decide how long a watch drains its battery for a phone that
 * may have gone.
 *
 * - **At most one reading every [minSecondsBetweenReadings] seconds.** The sensor reports about once
 *   a second; the phone does not need that, and the radio is the watch's scarcest thing.
 * - **Silence ends the run after [silenceLimitMillis].** The phone refreshes `RUN_HR_START` while it
 *   wants readings, so five minutes with nothing from it means it is gone — out of range, closed, or
 *   killed — and an exercise left running for nobody is the battery cost this exists to prevent.
 */
data class RunHeartRatePolicy(
    val minSecondsBetweenReadings: Long = 5,
    val silenceLimitMillis: Long = 5 * 60 * 1000L,
    /** How long a run found left behind by a killed process waits for a start to adopt it. */
    val orphanGraceMillis: Long = 5 * 60 * 1000L,
) {
    /**
     * Whether a reading taken at [atSeconds] goes to the phone, given the one last sent. A reading
     * not newer than that by [minSecondsBetweenReadings] is held back, and so is a slightly older one:
     * a batch Health Services delivered late must not send the phone backwards in time.
     */
    fun shouldSend(atSeconds: Long, lastSentAtSeconds: Long?): Boolean = when {
        lastSentAtSeconds == null -> true
        // The wall clock stepped back (a sync, a time-zone-less correction): readings are now timed
        // before the last one sent, and holding them all until the clock catches up would silence the
        // run. A reading only a few seconds older is a late batch, and is still held.
        atSeconds < lastSentAtSeconds - minSecondsBetweenReadings -> true
        else -> atSeconds - lastSentAtSeconds >= minSecondsBetweenReadings
    }

    fun millisUntilOrphanExpired(nowMillis: Long, foundAtMillis: Long): Long =
        (orphanGraceMillis - (nowMillis - foundAtMillis)).coerceAtLeast(0)

    fun isSilent(nowMillis: Long, lastContactMillis: Long): Boolean =
        nowMillis - lastContactMillis >= silenceLimitMillis

    fun millisUntilSilent(nowMillis: Long, lastContactMillis: Long): Long =
        (silenceLimitMillis - (nowMillis - lastContactMillis)).coerceAtLeast(0)

    companion object {
        /** What the wire's `u16` can carry, and a heart rate no sensor reading rounds to nothing of. */
        private const val MAX_BPM = 0xFFFF

        /**
         * A sample as a whole bpm, or `null` when it is no reading: not a number, not above zero, or
         * rounding to less than one. A zero is a sensor saying it has nothing, and **is never sent**.
         */
        fun wholeBpm(bpm: Double): Int? {
            if (!HeartRatePerSet.isReading(bpm)) return null
            val whole = HeartRatePerSet.wholeBpm(bpm)
            return whole.takeIf { it in 1..MAX_BPM }
        }
    }
}
