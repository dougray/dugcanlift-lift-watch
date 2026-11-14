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
) {
    /**
     * Whether a reading taken at [atSeconds] goes to the phone, given the one last sent. A reading
     * not newer than that by [minSecondsBetweenReadings] is held back, and so is an older one: a
     * batch Health Services delivered late must not send the phone backwards in time.
     */
    fun shouldSend(atSeconds: Long, lastSentAtSeconds: Long?): Boolean =
        lastSentAtSeconds == null || atSeconds - lastSentAtSeconds >= minSecondsBetweenReadings

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
