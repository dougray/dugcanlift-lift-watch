package com.dugcanlift.liftwear

import com.dugcanlift.liftkit.GuidedSession
import com.dugcanlift.liftkit.RestTimer
import com.dugcanlift.liftkit.SessionSnapshot
import com.dugcanlift.liftkit.WeightUnit
import com.dugcanlift.liftkit.WorkoutDraft
import com.dugcanlift.liftkit.isScheduledFor
import com.dugcanlift.liftkit.dayKey
import com.dugcanlift.liftkit.lastSetReport
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import com.dugcanlift.liftkit.link.PrescribedSet
import com.dugcanlift.liftkit.toFinishedSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The workout the watch is training right now.
 *
 * Everything in here is a thin shell over the value types in `:liftkit` — [WorkoutDraft],
 * [GuidedSession], [RestTimer] — which hold every rule and are unit tested without an Android
 * runtime. This class owns three things those cannot: the current values, writing them to disk after
 * every edit, and handing a set to the radio.
 *
 * **It writes locally first and only then tells the phone.** A send that fails must never cost the
 * lifter their set, and the phone is a Bluetooth peripheral away with a body between the two.
 *
 * **A session is persisted after every single edit.** Not on a timer and not on pause: Wear will kill
 * this process while the watch is on a wrist between sets, and there is no `transferUserInfo` queue
 * behind this link to re-deliver anything.
 */
class SessionController(
    private val store: SessionStore,
    private val link: PhoneLinkPeripheral,
    private val heart: HeartRateRecorder?,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val _draft = MutableStateFlow<WorkoutDraft?>(null)
    val draft: StateFlow<WorkoutDraft?> = _draft.asStateFlow()

    private val _guided = MutableStateFlow<GuidedSession?>(null)
    val guided: StateFlow<GuidedSession?> = _guided.asStateFlow()

    private val _rest = MutableStateFlow(RestTimer())
    val rest: StateFlow<RestTimer> = _rest.asStateFlow()

    private val _unit = MutableStateFlow(store.unit)
    val unit: StateFlow<WeightUnit> = _unit.asStateFlow()

    /** How many finished sessions the phone has not acknowledged. Shown on the summary page. */
    private val _pending = MutableStateFlow(store.outbox.count)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    init {
        // A session interrupted mid-set comes back exactly where it was, plan and all.
        store.session?.let { snapshot ->
            _draft.value = snapshot.draft.takeIf { !it.isFinished }
            _guided.value = if (snapshot.draft.isFinished) null else snapshot.guidedSession()
            _rest.value = snapshot.restTimer()
        }
        link.onPlan = { plan -> store.receive(plan, nowSeconds()) }
        link.onSessionAcknowledged = { sessionId ->
            store.acknowledge(sessionId)
            _pending.value = store.outbox.count
        }
        link.onReady = { flushOutbox() }
    }

    private fun nowSeconds(): Long = clockMillis() / 1000

    /** Today's plan, when the phone has pushed one for today. `null` is not a failure. */
    fun todaysPlan(): Plan? {
        val plan = store.plan?.plan() ?: return null
        return plan.takeIf { it.isScheduledFor(dayKey(nowSeconds())) }
    }

    fun setUnit(value: WeightUnit) {
        store.unit = value
        _unit.value = value
    }

    // ---- starting ---------------------------------------------------------------------------------

    /**
     * Starts today's plan: one draft exercise per planned exercise, in the plan's own order, and a
     * [GuidedSession] tracking position through it.
     *
     * What gets logged is an ordinary [WorkoutDraft] — the plan decides what to put in front of the
     * lifter, never what a set *is* — so everything downstream behaves exactly as it does for a
     * workout with no plan at all.
     */
    fun startPlanned(): Boolean {
        val plan = todaysPlan()?.takeIf { it.exercises.isNotEmpty() } ?: return false
        var workout = WorkoutDraft(
            id = idFactory(), name = plan.name,
            startedAtEpochSeconds = nowSeconds(), updatedAtEpochSeconds = nowSeconds(),
        )
        plan.exercises.forEachIndexed { index, exercise ->
            workout = workout.addExercise(
                // The plan carries no exercise ids (PLAN-FORMAT gives workouts none), so position is
                // the reference -- and it is stable, because the draft is built from the plan once and
                // the plan is never swapped underneath a running session.
                refId = "plan:$index", name = exercise.name, equipment = exercise.equipment,
                nowEpochSeconds = nowSeconds(),
            ) { "${workout.id}:$index" }
        }
        _draft.value = workout
        _guided.value = GuidedSession.start(plan)
        _rest.value = RestTimer()
        heart?.start()
        persist()
        return true
    }

    /** A workout with no plan: the free-entry flow, with exercises added as they are trained. */
    fun startFreeWorkout(name: String = "Workout") {
        _draft.value = WorkoutDraft(
            id = idFactory(), name = name,
            startedAtEpochSeconds = nowSeconds(), updatedAtEpochSeconds = nowSeconds(),
        )
        _guided.value = null
        _rest.value = RestTimer()
        heart?.start()
        persist()
    }

    fun addExercise(name: String, equipment: String?, refId: String) {
        val current = _draft.value ?: return
        _draft.value = current.addExercise(refId, name, equipment, nowSeconds(), idFactory)
        persist()
    }

    // ---- where the guided session is ---------------------------------------------------------------

    /** The draft exercise the guided session is on, or `null` when nothing is being guided. */
    fun guidedExerciseId(): String? {
        val guided = _guided.value ?: return null
        val draft = _draft.value ?: return null
        val index = guided.currentExerciseIndex ?: return null
        return draft.exercises.getOrNull(index)?.id
    }

    /** The prescription for the set about to be logged, or `null` when this is not the guided exercise. */
    fun prescription(exerciseId: String): PrescribedSet? =
        if (guidedExerciseId() == exerciseId) _guided.value?.currentPrescription else null

    /**
     * The side the next set should be logged on — a **suggestion**, not a decision: the log records
     * what happened while the plan only asked, so the screen starts here and the lifter can move it.
     */
    fun suggestedSide(exerciseId: String): LogSide? =
        if (guidedExerciseId() == exerciseId) _guided.value?.currentSide else null

    /** Moves the guided session to whichever exercise the lifter opened, so training out of order is a
     *  tap rather than a wrong prescription. */
    fun focus(exerciseId: String) {
        val guided = _guided.value ?: return
        val index = _draft.value?.exercises?.indexOfFirst { it.id == exerciseId } ?: return
        if (index >= 0) _guided.value = guided.select(index)
        persist()
    }

    // ---- logging ------------------------------------------------------------------------------------

    /**
     * Logs one set, starts the rest the **set just performed** prescribed, and tells the phone.
     *
     * The rest is that set's, not the next set's and not the next exercise's, which is why
     * [GuidedSession.recordSet] hands the performed prescription back. A prescription with no rest
     * seconds gets [RestTimer.DEFAULT_SECONDS] — never zero rest.
     */
    fun logSet(exerciseId: String, weightKg: Double, reps: Int, rpe: Double?, side: LogSide?) {
        val current = _draft.value ?: return
        val updated = current.logSet(
            exerciseId = exerciseId, weightKg = weightKg, reps = reps, rpe = rpe, side = side,
            nowEpochSeconds = nowSeconds(), idFactory = idFactory,
        )
        if (updated === current) return
        _draft.value = updated

        var prescribedRest: Int? = null
        if (guidedExerciseId() == exerciseId) {
            val recorded = _guided.value!!.recordSet(side)
            _guided.value = recorded.session
            prescribedRest = recorded.performed?.restSeconds
        }
        _rest.value = RestTimer().forInterval(prescribedRest).started(clockMillis())
        persist()
        // Optional and last: a phone on the bench stays in step, and nothing is lost if it does not
        // arrive -- the finished session is the source of truth and it is already on disk.
        updated.lastSetReport(exerciseId)?.let(link::reportSet)
    }

    /** Undoes the last set of an exercise — the one edit a wrist needs, for a mis-tap. */
    fun removeLastSet(exerciseId: String) {
        val current = _draft.value ?: return
        _draft.value = current.removeLastSet(exerciseId, nowSeconds())
        persist()
    }

    fun startRest() {
        _rest.value = _rest.value.started(clockMillis())
        persist()
    }

    fun skipRest() {
        _rest.value = _rest.value.stopped()
        persist()
    }

    // ---- finishing -----------------------------------------------------------------------------------

    /**
     * Ends the workout: on disk as a finished session first, then handed to the radio, then off the
     * screen. In that order, because everything after the first step can fail silently.
     */
    fun finish() {
        val current = _draft.value ?: return
        val finished = current.finished(nowSeconds())
        store.enqueue(finished.toFinishedSession())
        _pending.value = store.outbox.count
        heart?.stop()
        _draft.value = null
        _guided.value = null
        _rest.value = RestTimer()
        store.session = null
        flushOutbox()
    }

    /** Asks the phone to push today's plan. Fire and forget: the answer arrives when it arrives. */
    fun requestPlan() = link.requestPlan()

    /** Everything the phone has not acknowledged, offered again. Safe to call at any time: sending a
     *  session twice is safe, and the phone acknowledges a stored one again. */
    fun flushOutbox() {
        store.outbox.sessions().forEach(link::sendFinishedSession)
        _pending.value = store.outbox.count
    }

    private fun persist() {
        val current = _draft.value
        store.session = current?.let { SessionSnapshot.of(it, _guided.value, _rest.value) }
    }
}
