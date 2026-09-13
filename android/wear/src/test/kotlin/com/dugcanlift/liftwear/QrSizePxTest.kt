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

    @Test fun `square display uses the full width when nothing is reserved`() {
        val size = QrBitmap.qrSizePx(454, isRound = false)
        assertEquals(454, size)
    }

    /** I3: qrSizePx returned the full width on a square display, so the code spanned 436 of 454 px
     *  and the opaque BottomCenter "n / total" caption was drawn across its lower timing run. The
     *  caption band must sit outside the code's bounds — and because the code is centred in the Box,
     *  clearing the bottom band means the same inset at the top. */
    @Test fun `a square display leaves the caption band clear above and below the code`() {
        val width = 454
        val reserve = 60                     // 20 sp line height + 10 dp at density 2.0
        val size = QrBitmap.qrSizePx(width, isRound = false, captionReservePx = reserve)
        val topOfCode = (width - size) / 2
        assertTrue("code must not reach the caption band", topOfCode >= reserve)
        assertTrue("code must not reach the caption band", topOfCode + size <= width - reserve)
    }

    /** The same reserve must never make the round case larger, only smaller: the inscribed square
     *  is still the binding constraint at every real density. */
    @Test fun `the caption reserve never widens the round case`() {
        listOf(454 to 60, 384 to 50, 320 to 45).forEach { (width, reserve) ->
            val inscribed = QrBitmap.qrSizePx(width, isRound = true)
            val reserved = QrBitmap.qrSizePx(width, isRound = true, captionReservePx = reserve)
            assertTrue("$width: $reserved > $inscribed", reserved <= inscribed)
            assertTrue("$width: reserve should not dominate the inscription", reserved > width / 2)
        }
    }

    @Test fun `round is strictly smaller than square at the same width`() {
        val round = QrBitmap.qrSizePx(454, isRound = true)
        val square = QrBitmap.qrSizePx(454, isRound = false)
        assertTrue(round < square)
    }
}
