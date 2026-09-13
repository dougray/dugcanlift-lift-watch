package com.dugcanlift.liftwear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1: a full-bleed QR on a round display clips its corner finder patterns. qrSizePx must inscribe
 *  the code in the circle instead. Pure JVM math — no Robolectric needed. */
class QrSizePxTest {
    @Test fun `round 454px display inscribes the code within the circle`() {
        val size = QrBitmap.qrSizePx(454, isRound = true)
        assertTrue("expected <= 321, was $size", size <= 321)
        assertTrue("expected > 300, was $size", size > 300)
    }

    @Test fun `square display uses the full width`() {
        val size = QrBitmap.qrSizePx(454, isRound = false)
        assertEquals(454, size)
    }

    @Test fun `round is strictly smaller than square at the same width`() {
        val round = QrBitmap.qrSizePx(454, isRound = true)
        val square = QrBitmap.qrSizePx(454, isRound = false)
        assertTrue(round < square)
    }
}
