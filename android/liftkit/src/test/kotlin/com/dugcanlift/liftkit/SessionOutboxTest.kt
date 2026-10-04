package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.FinishedExercise
import com.dugcanlift.liftkit.link.FinishedSession
import com.dugcanlift.liftkit.link.LogSide
import com.dugcanlift.liftkit.link.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A finished session is kept until the phone says it has it. A send is not a receipt. */
class SessionOutboxTest {

    private fun session(id: String, revision: Int = 1) = FinishedSession(
        sessionId = id, revision = revision, day = "2026-09-25", name = "Push",
        startedAtEpochSeconds = 1_758_800_000L,
        exercises = listOf(
            FinishedExercise(
                "Bench Press", equipment = "barbell",
                sets = listOf(LoggedSet(reps = 6), LoggedSet(weightKg = 40.0, reps = 8, side = LogSide.LEFT)),
            )
        ),
    )

    @Test fun `a session stays until an ack names it`() {
        var outbox = SessionOutbox().with(session("a")).with(session("b"))
        assertEquals(2, outbox.count)
        outbox = outbox.acknowledging("a")
        assertEquals(listOf("b"), outbox.sessions().map { it.sessionId })
        outbox = outbox.acknowledging("b")
        assertTrue(outbox.isEmpty)
    }

    @Test fun `an ack for something never sent changes nothing`() {
        val outbox = SessionOutbox().with(session("a"))
        assertEquals(outbox, outbox.acknowledging("z"))
    }

    @Test fun `a re-send at a newer revision replaces its own earlier copy`() {
        val outbox = SessionOutbox().with(session("a", revision = 1)).with(session("a", revision = 2))
        assertEquals(1, outbox.count)
        assertEquals(2, outbox.sessions().single().revision)
    }

    @Test fun `what comes back off disk is the session that went in, blank weight included`() {
        val back = SessionOutbox.decode(SessionOutbox.encode(SessionOutbox().with(session("a"))))
        val restored = back.sessions().single()
        assertEquals(session("a"), restored)
        // Six reps with no weight: still no weight, not a lift of nothing.
        assertEquals(null, restored.exercises[0].sets[0].weightKg)
        assertEquals(LogSide.LEFT, restored.exercises[0].sets[1].side)
    }

    @Test fun `nothing stored is an empty outbox, and so is anything unreadable`() {
        assertTrue(SessionOutbox.decode(null).isEmpty)
        assertTrue(SessionOutbox.decode("nonsense").isEmpty)
    }

    @Test fun `one unreadable entry does not hold up the others`() {
        val outbox = SessionOutbox(mapOf("a" to "!!!", "b" to SessionOutbox().with(session("b")).pending.getValue("b")))
        assertEquals(listOf("b"), outbox.sessions().map { it.sessionId })
    }
}
