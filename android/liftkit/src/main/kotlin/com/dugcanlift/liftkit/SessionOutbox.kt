package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.FinishedSession
import com.dugcanlift.liftkit.link.LinkPayloads
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Finished sessions the phone has not acknowledged yet, on disk.
 *
 * The watch's radio only runs while LIFT is open, and a lifter finishes a workout and drops their
 * wrist. **A send is not a receipt**: a session stays here until an `ACK` naming its id arrives, and
 * is offered again on the next launch and on every reconnect. That is LIFT for Apple Watch's
 * `UnsentSessionLog` rule, and it matters more here — there is no OS-level queue behind this link to
 * hold anything if the app dies.
 *
 * Keyed by session id, so a session re-sent at a newer revision replaces its own earlier copy rather
 * than joining it. The session travels as `LinkPayloads.encodeSession`'s own bytes, for the reason
 * [SessionSnapshot] keeps a plan that way: one encoding, already pinned by `LinkWireFixtureTest`.
 */
@Serializable
data class SessionOutbox(
    /** Session id to base64 of `LinkPayloads.encodeSession`. */
    val pending: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = pending.isEmpty()
    val count: Int get() = pending.size

    fun with(session: FinishedSession): SessionOutbox = copy(
        pending = pending + (session.sessionId to Base64.getEncoder().encodeToString(LinkPayloads.encodeSession(session)))
    )

    /** Takes a session out. Called on an `ACK`, and on nothing else. */
    fun acknowledging(sessionId: String): SessionOutbox =
        if (pending.containsKey(sessionId)) copy(pending = pending - sessionId) else this

    /**
     * Everything still owed, decoded. An entry whose bytes will not read back is dropped from the
     * result rather than failing the rest: one unreadable session must not hold up the others.
     */
    fun sessions(): List<FinishedSession> = pending.values.mapNotNull { encoded ->
        runCatching { LinkPayloads.decodeSession(Base64.getDecoder().decode(encoded)) }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(outbox: SessionOutbox): String = json.encodeToString(outbox)

        fun decode(text: String?): SessionOutbox =
            text?.let { runCatching { json.decodeFromString<SessionOutbox>(it) }.getOrNull() } ?: SessionOutbox()
    }
}
