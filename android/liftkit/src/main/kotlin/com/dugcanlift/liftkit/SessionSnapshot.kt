package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LinkPayloads
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.Plan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * A session on disk.
 *
 * **Wear needs this in a way the Apple watch does not.** There, a plan the phone pushed is re-sent by
 * `transferUserInfo` whenever the watch wakes, so losing it to a process death costs nothing. Here
 * the radio is off unless LIFT is open and the phone is a Bluetooth peripheral away, so a session
 * lost between sets is a session the lifter has to walk back to their phone to get again.
 *
 * The plan travels **as the wire delivered it** — `LinkPayloads.encodePlan`'s own bytes, base64'd —
 * rather than as a second JSON shape of its own. One encoding, already pinned by
 * `LinkWireFixtureTest`, and the `link` package keeps its rule of importing no serialization library.
 * It is the same choice Coach for iPhone makes for a day's outdoor tuples.
 *
 * A side is [LogSide.code] with **0 for both**, which is how the wire spells absent.
 */
@Serializable
data class SessionSnapshot(
    val draft: WorkoutDraft,
    /** Base64 of `LinkPayloads.encodePlan`. Absent for a session started with no plan. */
    val planWire: String? = null,
    /** Parallel to the plan's exercises. `0` is a two-sided set. */
    val loggedSides: List<List<Int>> = emptyList(),
    val exerciseIndex: Int = 0,
    val restIntervalSeconds: Int = RestTimer.DEFAULT_SECONDS,
    val restStartedAtMillis: Long? = null,
) {
    /**
     * The session this snapshot describes, or `null` when it held no plan — which is a free-entry
     * workout, not a failure. A plan whose bytes will not decode is also `null`: the draft is the
     * lifter's work and must survive, and losing the guidance is the smaller loss.
     */
    fun guidedSession(): GuidedSession? {
        val plan: Plan = planWire
            ?.let { runCatching { LinkPayloads.decodePlan(Base64.getDecoder().decode(it)) }.getOrNull() }
            ?: return null
        val sides = plan.exercises.indices.map { index ->
            loggedSides.getOrNull(index).orEmpty().map { LogSide.from(it) }
        }
        return GuidedSession(plan, sides, exerciseIndex.coerceIn(0, maxOf(0, plan.exercises.size - 1)))
    }

    fun restTimer(): RestTimer = RestTimer(restIntervalSeconds, restStartedAtMillis)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun of(draft: WorkoutDraft, guided: GuidedSession?, rest: RestTimer): SessionSnapshot =
            SessionSnapshot(
                draft = draft,
                planWire = guided?.let { Base64.getEncoder().encodeToString(LinkPayloads.encodePlan(it.plan)) },
                loggedSides = guided?.loggedSides?.map { sides -> sides.map { it?.code ?: 0 } }.orEmpty(),
                exerciseIndex = guided?.exerciseIndex ?: 0,
                restIntervalSeconds = rest.intervalSeconds,
                restStartedAtMillis = rest.startedAtMillis,
            )

        fun encode(snapshot: SessionSnapshot): String = json.encodeToString(snapshot)

        /** `null` for anything that will not read back. The caller then has no session in progress,
         *  which is the same state a fresh install is in. */
        fun decode(text: String?): SessionSnapshot? =
            text?.let { runCatching { json.decodeFromString<SessionSnapshot>(it) }.getOrNull() }
    }
}

/**
 * The plan the phone last pushed, kept between launches with the identity the reconciliation rule
 * needs: a newer revision for the same id replaces, the same or an older one is ignored, and a
 * different id always replaces because the phone decides what today is.
 */
@Serializable
data class StoredPlan(
    /** Base64 of `LinkPayloads.encodePlan`, as received. */
    val wire: String,
    val planId: String,
    val revision: Int,
    val receivedAtEpochSeconds: Long,
) {
    fun plan(): Plan? =
        runCatching { LinkPayloads.decodePlan(Base64.getDecoder().decode(wire)) }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun of(plan: Plan, receivedAtEpochSeconds: Long): StoredPlan = StoredPlan(
            wire = Base64.getEncoder().encodeToString(LinkPayloads.encodePlan(plan)),
            planId = plan.planId,
            revision = plan.revision,
            receivedAtEpochSeconds = receivedAtEpochSeconds,
        )

        fun encode(stored: StoredPlan): String = json.encodeToString(stored)

        fun decode(text: String?): StoredPlan? =
            text?.let { runCatching { json.decodeFromString<StoredPlan>(it) }.getOrNull() }

        /**
         * Whether [incoming] should replace [held] — the schema's own conflict rule, and the same one
         * `WorkoutStore` applies to a workout. A different plan id always wins.
         */
        fun accepts(held: StoredPlan?, incoming: Plan): Boolean =
            held == null || held.planId != incoming.planId || incoming.revision > held.revision
    }
}
