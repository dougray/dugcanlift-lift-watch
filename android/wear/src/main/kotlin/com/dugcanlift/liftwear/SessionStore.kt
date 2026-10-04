package com.dugcanlift.liftwear

import android.content.Context
import com.dugcanlift.liftkit.SessionOutbox
import com.dugcanlift.liftkit.SessionSnapshot
import com.dugcanlift.liftkit.StoredPlan
import com.dugcanlift.liftkit.WeightUnit
import com.dugcanlift.liftkit.link.FinishedSession
import com.dugcanlift.liftkit.link.Plan

/**
 * Everything about lifting that has to outlive the process: the plan the phone pushed, the session in
 * progress, the finished sessions the phone has not acknowledged, and which unit the lifter reads.
 *
 * SharedPreferences, for the reason [PrefsLogStorage] and [PhoneLinkStore] use it: short values, no
 * dependency, and `allowBackup="false"` like everything else here. The **decoding** lives in
 * `:liftkit` ([SessionSnapshot], [StoredPlan], [SessionOutbox]) because that is where it can be unit
 * tested; this file is only the slot it goes in.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("liftwear_session", Context.MODE_PRIVATE)

    // ---- the plan the phone pushed --------------------------------------------------------------

    var plan: StoredPlan?
        get() = StoredPlan.decode(prefs.getString(KEY_PLAN, null))
        private set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PLAN) else putString(KEY_PLAN, StoredPlan.encode(value))
            }.apply()
        }

    /**
     * Stores a pushed plan under the contract's own conflict rule, answering whether it was taken —
     * which is what the `ACK` outcome is for. `commit()`, not `apply()`: a plan arrives over a radio
     * and the app may be killed the moment the screen goes off, and a plan the watch said it had
     * must be a plan the watch has.
     */
    fun receive(plan: Plan, nowEpochSeconds: Long): Boolean {
        val held = this.plan
        if (!StoredPlan.accepts(held, plan)) return false
        prefs.edit()
            .putString(KEY_PLAN, StoredPlan.encode(StoredPlan.of(plan, nowEpochSeconds)))
            .commit()
        return true
    }

    // ---- the session in progress -----------------------------------------------------------------

    var session: SessionSnapshot?
        get() = SessionSnapshot.decode(prefs.getString(KEY_SESSION, null))
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_SESSION) else putString(KEY_SESSION, SessionSnapshot.encode(value))
            }.apply()
        }

    // ---- what the phone has not acknowledged -----------------------------------------------------

    var outbox: SessionOutbox
        get() = SessionOutbox.decode(prefs.getString(KEY_OUTBOX, null))
        private set(value) {
            prefs.edit().putString(KEY_OUTBOX, SessionOutbox.encode(value)).apply()
        }

    /** `commit()` for the same reason [receive] does: this is the only copy of a finished workout. */
    fun enqueue(session: FinishedSession) {
        prefs.edit().putString(KEY_OUTBOX, SessionOutbox.encode(outbox.with(session))).commit()
    }

    fun acknowledge(sessionId: String) {
        outbox = outbox.acknowledging(sessionId)
    }

    // ---- what the lifter reads --------------------------------------------------------------------

    /**
     * Pounds until the lifter says otherwise, matching LIFT for Apple Watch's default and LIFT
     * Android's storage unit. The wire is always kilograms; this is the display and nothing else.
     */
    var unit: WeightUnit
        get() = WeightUnit.from(prefs.getString(KEY_UNIT, null))
        set(value) { prefs.edit().putString(KEY_UNIT, value.name).apply() }

    private companion object {
        const val KEY_PLAN = "pushed_plan"
        const val KEY_SESSION = "session_in_progress"
        const val KEY_OUTBOX = "session_outbox"
        const val KEY_UNIT = "weight_unit"
    }
}
