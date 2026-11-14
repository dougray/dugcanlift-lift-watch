package com.dugcanlift.liftwear

import com.dugcanlift.liftkit.HeartRatePerSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What [RunHeartRateMode] needs from Health Services' exercise client, and nothing more, so the mode's
 * rules can be tested against a fake. [HealthServicesExerciseClient] is the real one.
 */
interface ExerciseClientLike {
    /** Whose exercise, if anyone's, is running on this watch right now. */
    enum class Tracked { NONE, OWNED_IN_PROGRESS, OTHER_APP_IN_PROGRESS }

    /** Whether the heart-rate permission is granted. Without it nothing here may start. */
    val hasPermission: Boolean

    suspend fun trackedStatus(): Tracked

    /**
     * Starts a `RUNNING` exercise with GPS off and heart rate its only data type, and delivers each
     * batch of readings to [onReadings] — timed in epoch seconds, as they were taken. [onEnded] is
     * called if the exercise ends without [end] having been called. Throws when Health Services
     * would not start it, having left nothing registered.
     */
    suspend fun startRun(onReadings: (List<HeartRatePerSet.Sample>) -> Unit, onEnded: () -> Unit)

    /** Ends the exercise [startRun] started and stops listening. */
    suspend fun end()
}

/**
 * Heart rate for a run, read at the wrist and handed to the phone — the watch half of "run heart
 * rate from any source" (`docs/LINK-PROTOCOL.md` "Version 5").
 *
 * **Tracked and shown, never targeted**: the watch measures and sends. It keeps nothing — no reading
 * is written down, and nothing about the run is stored here — and it has no network. What to show,
 * and when a source that has gone quiet stops being shown, is the phone's.
 *
 * **It owns one exercise and no other.** It starts only when the watch has no exercise at all: not a
 * lifting session's (this app's own, tracked by Health Services as owned), not another app's, and
 * not while the lifting session is on screen ([liftingActive]). It ends only what it started, so the
 * lifting exercise is never touched; when a lifting session starts while a run is going, [yieldToLifting]
 * lets go of the run **without** ending anything, because the lifting exercise's own start replaces it
 * and an end issued here could land on the new one.
 *
 * **It always ends what it started.** [stop] is idempotent and safe at any moment, including while the
 * exercise is still starting. It is called on the phone's `RUN_HR_STOP`, on link loss, and by a timer:
 * the phone refreshes `RUN_HR_START` while it wants readings, so [RunHeartRatePolicy.silenceLimitMillis]
 * with no start or other word from it means the phone is gone, and an exercise running for nobody
 * drains the battery. A [start] while already running is only that refresh — one exercise, never two.
 *
 * **Refusals cost nothing and say nothing:** no permission, another exercise, an exercise Health
 * Services would not start. Each leaves the mode idle with nothing to end, and the watch's own
 * lifting is unaffected.
 *
 * Calls may come from any thread; state is guarded, and every call into Health Services runs one at
 * a time on [scope].
 */
class RunHeartRateMode(
    private val exercise: ExerciseClientLike,
    private val send: (bpm: Int, atEpochSec: Long) -> Unit,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val liftingActive: () -> Boolean = { false },
    private val policy: RunHeartRatePolicy = RunHeartRatePolicy(),
) {
    private val lock = Any()
    private val clientCalls = Mutex()

    /** The phone wants readings. Set by [start], cleared by every way a run ends. */
    private var wanted = false

    /** An exercise this mode started is live. */
    private var active = false

    /** Bumped whenever the live exercise changes hands, so a late callback from an old one is ignored. */
    private var epoch = 0
    private var lastContactMillis = 0L
    private var lastSentAtSeconds: Long? = null
    private var watchdog: Job? = null

    /** Whether an exercise this mode started is running. */
    val isRunning: Boolean get() = synchronized(lock) { active && wanted }

    /**
     * `RUN_HR_START`. Starts a run, or — when one is already starting or running — only records that
     * the phone is still there. A lifting session or another exercise, a missing permission: nothing
     * happens.
     */
    fun start() {
        val now = clockMillis()
        val begin = synchronized(lock) {
            lastContactMillis = now
            if (wanted) return                      // one exercise: this is only the refresh
            if (liftingActive() || !exercise.hasPermission) return
            wanted = true
            lastSentAtSeconds = null
            watchdog = scope.launch { watchSilence() }
            true
        }
        if (begin) scope.launch { reconcile() }
    }

    /** `RUN_HR_STOP`. Ends the exercise this mode started, if it started one. Idempotent. */
    fun stop() {
        val had = synchronized(lock) {
            val was = wanted
            wanted = false
            watchdog?.cancel()
            watchdog = null
            was
        }
        if (had) scope.launch { reconcile() }
    }

    /** The link went away. Nothing is listening, so nothing keeps measuring. */
    fun linkLost() = stop()

    /** The phone said something. A run it forgot to refresh is still one it is talking about. */
    fun contact() {
        val now = clockMillis()
        synchronized(lock) { if (wanted) lastContactMillis = now }
    }

    /**
     * A lifting session is starting and will start its own exercise, which replaces this one. Lets go
     * without ending: see the class comment.
     */
    fun yieldToLifting() {
        synchronized(lock) {
            wanted = false
            active = false
            epoch++
            watchdog?.cancel()
            watchdog = null
        }
    }

    // ---- readings ---------------------------------------------------------------------------------

    private fun onReadings(forEpoch: Int, samples: List<HeartRatePerSet.Sample>) {
        // The newest sample that is a reading at all; a zero or a NaN is skipped, not sent as itself.
        val newest = samples.filter { RunHeartRatePolicy.wholeBpm(it.bpm) != null }.maxByOrNull { it.atSeconds }
            ?: return
        val bpm = RunHeartRatePolicy.wholeBpm(newest.bpm) ?: return
        val at = newest.atSeconds.toLong()
        synchronized(lock) {
            if (forEpoch != epoch || !wanted || !active) return
            if (!policy.shouldSend(at, lastSentAtSeconds)) return
            lastSentAtSeconds = at
        }
        // Outside the lock: the radio is none of its business. A failed send is a missed reading.
        runCatching { send(bpm, at) }
    }

    private fun onEnded(forEpoch: Int) {
        synchronized(lock) {
            if (forEpoch != epoch) return
            // Something else ended our exercise (or we did). There is nothing left to end.
            wanted = false
            active = false
            epoch++
            watchdog?.cancel()
            watchdog = null
        }
    }

    // ---- the one place that talks to Health Services -----------------------------------------------

    /** Brings the exercise in line with [wanted], under the mutex so starts and ends never overlap. */
    private suspend fun reconcile() = clientCalls.withLock {
        while (true) {
            val (want, live) = synchronized(lock) { wanted to active }
            when {
                want && !live -> if (!begin()) return
                !want && live -> {
                    synchronized(lock) { active = false; epoch++ }
                    runCatching { exercise.end() }
                }
                else -> return
            }
        }
    }

    /** Starts the exercise. Returns false, with nothing running and nothing wanted, when it cannot. */
    private suspend fun begin(): Boolean {
        val myEpoch = synchronized(lock) { ++epoch }
        val tracked = runCatching { exercise.trackedStatus() }.getOrNull()
        val refused = tracked != ExerciseClientLike.Tracked.NONE || liftingActive() || !exercise.hasPermission
        if (refused) return giveUp()
        val started = runCatching {
            exercise.startRun(
                onReadings = { onReadings(myEpoch, it) },
                onEnded = { onEnded(myEpoch) },
            )
        }.isSuccess
        if (!started) return giveUp()
        val handedOver = synchronized(lock) {
            if (epoch != myEpoch) true else { active = true; false }
        }
        // A lifting session took the exercise while it was starting: it is the lifting session's now.
        return !handedOver
    }

    private fun giveUp(): Boolean {
        synchronized(lock) {
            wanted = false
            watchdog?.cancel()
            watchdog = null
        }
        return false
    }

    // ---- the phone that vanished --------------------------------------------------------------------

    private suspend fun watchSilence() {
        while (true) {
            val wait = synchronized(lock) {
                if (!wanted) return
                policy.millisUntilSilent(clockMillis(), lastContactMillis)
            }
            if (wait <= 0) {
                stop()
                return
            }
            delay(wait)
        }
    }
}
