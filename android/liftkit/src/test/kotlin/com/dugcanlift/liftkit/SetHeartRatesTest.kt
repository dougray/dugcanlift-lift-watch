package com.dugcanlift.liftkit

import com.dugcanlift.liftkit.link.LogSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live half of heart rate per set: readings kept in memory, each set's figure worked out once its
 * window has closed, and the figure carried by the set wherever it goes — through an edit, a
 * snapshot, the outbox — and gone with it when it is deleted. [HeartRatePerSetTest] pins the rule
 * itself against the hand-worked fixture; this pins when the rule is asked and what is kept.
 */
class SetHeartRatesTest {
    private val start = 1_762_000_000L

    private var ids = 0
    private fun nextId() = "set-${ids++}"

    private fun draft() = WorkoutDraft(
        id = "session-1", name = "Push", startedAtEpochSeconds = start, updatedAtEpochSeconds = start,
    ).addExercise("bench", "Bench Press", "barbell", nowEpochSeconds = start) { "bench" }
        .addExercise("row", "Row", nowEpochSeconds = start) { "row" }

    private fun WorkoutDraft.log(exerciseId: String, at: Long, side: LogSide? = null) =
        logSet(exerciseId, weightKg = 60.0, reps = 8, side = side, nowEpochSeconds = at) { nextId() }

    private fun SetHeartRates.reading(at: Long, bpm: Double) = record((start + at).toDouble(), bpm)

    private fun WorkoutDraft.figures(): List<Int?> = exercises.flatMap { it.sets }.map { it.maxBpm }

    @Test fun `a set's window stays open until a reading from after it arrives`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 60)
        heart.reading(50, 120.0)
        heart.reading(75, 135.0)
        draft = heart.settle(draft, start + 76)
        // 75 s is inside [0, 80]: the window is still open, and Health Services may be holding more.
        assertEquals(listOf<Int?>(null), draft.figures())

        heart.reading(80, 138.0)           // the window's last instant, still inside it
        draft = heart.settle(draft, start + 81)
        assertEquals(listOf<Int?>(null), draft.figures())

        heart.reading(81, 170.0)           // after it: the window has closed, and 170 is not in it
        draft = heart.settle(draft, start + 82)
        assertEquals(listOf<Int?>(138), draft.figures())
    }

    @Test fun `the next tick closes a window, on whatever exercise`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 100)
        heart.reading(105, 140.0)
        draft = draft.log("row", start + 110)          // a superset: the row comes ten seconds later
        heart.reading(112, 150.0)
        draft = heart.settle(draft, start + 112)
        assertEquals(listOf<Int?>(140, null), draft.figures())

        heart.reading(131, 155.0)
        draft = heart.settle(draft, start + 131)
        assertEquals(listOf<Int?>(140, 150), draft.figures())
    }

    @Test fun `finishing closes every window still open, with the readings there are`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 60)
        heart.reading(62, 131.0)
        draft = heart.settle(draft, start + 63)
        assertEquals(listOf<Int?>(null), draft.figures())
        draft = heart.settle(draft, start + 64, finishing = true)
        assertEquals(listOf<Int?>(131), draft.figures())
    }

    @Test fun `no reading in a window is no figure, and nothing reads zero`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 60)
        heart.reading(70, 0.0)                         // a sensor with nothing to say
        heart.reading(200, 150.0)
        draft = heart.settle(draft, start + 200, finishing = true)
        assertEquals(listOf<Int?>(null), draft.figures())
        assertTrue(draft.exercises.flatMap { it.sets }.none { it.maxBpm == 0 })
    }

    @Test fun `a window is worked out once, and a figure is never replaced`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 60)
        heart.reading(70, 130.0)
        heart.reading(90, 150.0)
        draft = heart.settle(draft, start + 90)
        assertEquals(listOf<Int?>(130), draft.figures())
        val revision = draft.revision
        // Nothing new closed: the same draft back, no revision spent on nothing.
        assertSame(draft, heart.settle(draft, start + 91, finishing = true))
        assertEquals(revision, draft.revision)
    }

    @Test fun `a figure landing is an edit, and commits once`() {
        val heart = SetHeartRates()
        val logged = draft().log("bench", start + 60).log("bench", start + 70)
        heart.reading(65, 130.0)
        heart.reading(75, 131.0)
        heart.reading(95, 100.0)
        val settled = heart.settle(logged, start + 95)
        assertEquals(listOf<Int?>(130, 131), settled.figures())
        assertEquals(logged.revision + 1, settled.revision)
    }

    @Test fun `a deleted set takes its figure with it, and the others keep theirs`() {
        val heart = SetHeartRates()
        var draft = draft().log("bench", start + 60).log("row", start + 120)
        heart.reading(70, 130.0)
        heart.reading(125, 140.0)
        heart.reading(141, 100.0)
        draft = heart.settle(draft, start + 141)
        assertEquals(listOf<Int?>(130, 140), draft.figures())
        draft = draft.removeLastSet("row", start + 150)
        assertEquals(listOf<Int?>(130), draft.figures())
        assertEquals(130, draft.toFinishedSession().exercises[0].sets.single().maxBpm)
    }

    @Test fun `readings are kept for six minutes and no longer`() {
        val heart = SetHeartRates()
        val draft = draft()
        for (t in 0..1200 step 5) heart.reading(t.toLong(), 120.0)
        heart.settle(draft, start + 1200)
        // 360 s at one reading every five seconds, both ends kept.
        assertEquals(73, heart.heldSampleCount)
    }

    @Test fun `the session's average and maximum round as a set's figure does`() {
        val heart = SetHeartRates()
        assertNull(heart.session)
        heart.reading(10, 100.0)
        heart.reading(20, 141.5)
        heart.reading(30, 0.0)                         // not a reading: not in the average either
        assertEquals(SessionHeartRate(averageBpm = 121, maxBpm = 142), heart.session)
        heart.reset()
        assertNull(heart.session)
        assertNull(heart.newestSampleSeconds)
    }

    // ---- the figure travels with its set ----------------------------------------------------------

    @Test fun `a set's figure survives the snapshot, and a draft written before it reads with none`() {
        val draft = draft().log("bench", start + 60).withHeartRates(mapOf("set-0" to 142), start + 90)
        val back = SessionSnapshot.decode(SessionSnapshot.encode(SessionSnapshot.of(draft, null, RestTimer())))!!
        assertEquals(142, back.draft.exercises[0].sets.single().maxBpm)

        val old = """{"draft":{"id":"s","name":"Push","startedAtEpochSeconds":1,"updatedAtEpochSeconds":1,""" +
            """"exercises":[{"id":"e","exerciseRefId":"r","name":"Row","orderIndex":0,""" +
            """"sets":[{"id":"x","orderIndex":0,"weightKg":50.0,"reps":8,"completedAtEpochSeconds":5}]}]}}"""
        assertNull(SessionSnapshot.decode(old)!!.draft.exercises[0].sets.single().maxBpm)
    }

    @Test fun `the finished session carries every figure and the session's pair, and absent stays absent`() {
        val draft = draft().log("bench", start + 60).log("bench", start + 200, LogSide.LEFT)
            .withHeartRates(mapOf("set-1" to 151), start + 300)
        val finished = draft.toFinishedSession(heartRate = SessionHeartRate(118, 156))
        assertEquals(listOf(null, 151), finished.exercises[0].sets.map { it.maxBpm })
        assertEquals(LogSide.LEFT, finished.exercises[0].sets[1].side)
        assertEquals(118, finished.averageBpm)
        assertEquals(156, finished.maxBpm)

        val unmeasured = draft().log("bench", start + 60).toFinishedSession()
        assertFalse(unmeasured.recordsHeartRate)
    }

    @Test fun `the outbox keeps the whole session, heart rate included`() {
        val finished = draft().log("bench", start + 60).withHeartRates(mapOf("set-0" to 142), start + 90)
            .toFinishedSession(heartRate = SessionHeartRate(118, 156))
        val back = SessionOutbox.decode(SessionOutbox.encode(SessionOutbox().with(finished))).sessions().single()
        assertEquals(finished, back)
    }

    // ---- after the process was killed ----------------------------------------------------------------

    @Test fun `the session's pair survives a restart as a running total, never as readings`() {
        val before = SetHeartRates()
        before.reading(10, 120.0)
        before.reading(20, 160.0)
        val saved = before.totals!!
        assertEquals(SetHeartRates.Totals(count = 2, total = 280.0, highest = 160.0), saved)

        val after = SetHeartRates()
        after.resume(saved, draft())
        assertEquals(0, after.heldSampleCount)
        assertEquals(SessionHeartRate(averageBpm = 140, maxBpm = 160), after.session)
        after.reading(400, 110.0)
        // (120 + 160 + 110) / 3 = 130: the average covers both sides of the restart.
        assertEquals(SessionHeartRate(averageBpm = 130, maxBpm = 160), after.session)
    }

    @Test fun `a set ticked before the restart gets no figure from the readings after it`() {
        var draft = draft().log("bench", start + 60).withHeartRates(mapOf("set-0" to 142), start + 90)
            .log("bench", start + 200)                       // its window was open when the process died
        val heart = SetHeartRates()
        heart.resume(null, draft)
        heart.reading(205, 150.0)                            // inside set-1's window, but half of it is gone
        heart.reading(230, 120.0)
        draft = heart.settle(draft, start + 230)
        assertEquals(listOf<Int?>(142, null), draft.figures())

        draft = draft.log("row", start + 300)                // after the restart: worked out as usual
        heart.reading(310, 155.0)
        heart.reading(321, 100.0)
        draft = heart.settle(draft, start + 321)
        assertEquals(listOf<Int?>(142, null, 155), draft.figures())
        draft = heart.settle(draft, start + 400, finishing = true)
        assertEquals(listOf<Int?>(142, null, 155), draft.figures())
    }

    @Test fun `totals that make no sense are dropped, and no reading is no total`() {
        assertNull(SetHeartRates().totals)
        listOf(
            SetHeartRates.Totals(-1, 280.0, 160.0),
            SetHeartRates.Totals(2, Double.NaN, 160.0),
            SetHeartRates.Totals(2, 280.0, 0.0),
            SetHeartRates.Totals(0, 0.0, 0.0),
        ).forEach { junk ->
            val heart = SetHeartRates()
            heart.resume(junk, draft())
            assertNull("$junk", heart.session)
            assertNull("$junk", heart.totals)
        }
    }

    @Test fun `the totals ride in the snapshot, and a snapshot written before them reads as none`() {
        val totals = SetHeartRates.Totals(count = 3, total = 390.0, highest = 150.0)
        val snapshot = SessionSnapshot.of(draft(), null, RestTimer(), heartRateTotals = totals)
        assertEquals(totals, SessionSnapshot.decode(SessionSnapshot.encode(snapshot))!!.heartRateTotals)

        val old = """{"draft":{"id":"s","name":"Push","startedAtEpochSeconds":1,"updatedAtEpochSeconds":1,""" +
            """"exercises":[]}}"""
        assertNull(SessionSnapshot.decode(old)!!.heartRateTotals)
    }
}
