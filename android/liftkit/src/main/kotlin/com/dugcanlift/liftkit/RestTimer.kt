package com.dugcanlift.liftkit

/**
 * Rest between sets.
 *
 * **Derived from a start instant, never from a tick.** A port of LIFT for Apple Watch's `RestTimer`
 * and for the same reason: a watch suspends the app when the wrist drops, so a countdown driven by
 * a repeating job stops counting exactly when the lifter stops looking. Asking "how long since it
 * started" is right whether or not anything was running in between.
 *
 * A value type with no Android in it, so the arithmetic is unit tested rather than observed.
 * Immutable: [started] and [stopped] return a new timer, because a `var` on a Compose state holder
 * is the one place this rule could quietly grow a second copy.
 */
data class RestTimer(
    /** Seconds to rest. Never zero for "no opinion" — see [DEFAULT_SECONDS]. */
    val intervalSeconds: Int = DEFAULT_SECONDS,
    val startedAtMillis: Long? = null,
) {
    val isRunning: Boolean get() = startedAtMillis != null

    fun started(atMillis: Long): RestTimer = copy(startedAtMillis = atMillis)

    fun stopped(): RestTimer = copy(startedAtMillis = null)

    /** A fresh timer for [seconds], not yet running. `null` seconds means the sender had no opinion
     *  and the watch uses its own default — which is not the same as resting for zero. */
    fun forInterval(seconds: Int?): RestTimer =
        RestTimer(intervalSeconds = seconds?.takeIf { it > 0 } ?: DEFAULT_SECONDS)

    /** Seconds left, or `null` when the timer has not been started. */
    fun remainingSeconds(nowMillis: Long): Double? {
        val start = startedAtMillis ?: return null
        val elapsed = (nowMillis - start) / 1000.0
        return maxOf(0.0, intervalSeconds - elapsed)
    }

    fun hasFinished(nowMillis: Long): Boolean = (remainingSeconds(nowMillis) ?: return false) <= 0.0

    /** Fraction elapsed, 0..1, for a progress ring. */
    fun progress(nowMillis: Long): Double {
        if (intervalSeconds <= 0) return 0.0
        val remaining = remainingSeconds(nowMillis) ?: return 0.0
        return (1.0 - remaining / intervalSeconds).coerceIn(0.0, 1.0)
    }

    companion object {
        /**
         * What to rest when the plan prescribes nothing. Ninety seconds, the same number LIFT for
         * Apple Watch uses, so a set logged off plan rests the same on either wrist.
         */
        const val DEFAULT_SECONDS = 90

        /** "01:37". Rounds rather than truncates, so 96.6 s reads 01:37 and not 01:36. */
        fun format(seconds: Double): String {
            val total = Math.round(maxOf(0.0, seconds)).toInt()
            return "%02d:%02d".format(total / 60, total % 60)
        }
    }
}
