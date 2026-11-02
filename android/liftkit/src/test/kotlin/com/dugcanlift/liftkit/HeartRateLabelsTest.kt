package com.dugcanlift.liftkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The words, pinned: they are LIFT for Apple Watch's, LIFT Android's and LIFT iPhone's too, and they
 * change in every build or in none. Then the house rule for a number about someone's body — shown,
 * never graded — checked on the strings themselves.
 */
class HeartRateLabelsTest {
    @Test fun `a set reads max and the bpm, and a set with no figure reads nothing`() {
        assertEquals("max 142 bpm", HeartRateLabels.set(142))
        assertNull(HeartRateLabels.set(null))
        assertNull(HeartRateLabels.set(0))
    }

    @Test fun `the session reads its average and its maximum, and nothing when neither was measured`() {
        assertEquals("Heart rate: average 118 · max 156 bpm", HeartRateLabels.session(118, 156))
        assertEquals("Heart rate: average 118 · max 156 bpm", HeartRateLabels.session(SessionHeartRate(118, 156)))
        assertEquals("Heart rate: max 156 bpm", HeartRateLabels.session(null, 156))
        assertEquals("Heart rate: average 118 bpm", HeartRateLabels.session(118, null))
        assertNull(HeartRateLabels.session(null, null))
        assertNull(HeartRateLabels.session(0, 0))
        assertNull(HeartRateLabels.session(null))
    }

    @Test fun `nothing in these lines tells anyone what to do or grades a number`() {
        val lines = listOfNotNull(
            HeartRateLabels.set(142), HeartRateLabels.set(61), HeartRateLabels.set(199),
            HeartRateLabels.session(118, 156), HeartRateLabels.session(null, 156), HeartRateLabels.session(118, null),
        ).joinToString(" · ").lowercase(Locale.US)
        listOf(
            "zone", "recover", "ready", "rest", "%", "percent", "of max", "target", "goal", "should",
            "too ", "high", "low", "warning", "good", "great", "poor", "easy", "hard", "effort",
            "intensity", "fat burn", "cardio", "peak", "resting", "calorie", "trend", "!",
        ).forEach { word -> assertFalse("\"$word\" reached a screen: $lines", lines.contains(word)) }
    }
}
