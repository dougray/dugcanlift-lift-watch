package com.dugcanlift.liftwear
import com.dugcanlift.liftkit.FoodLogMeal
import com.dugcanlift.liftkit.LoggedFood
import com.dugcanlift.liftkit.StandaloneExport
import com.dugcanlift.liftkit.WatchFood
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrBitmapTest {
    @Test fun `a rendered code decodes back to its text`() {
        val text = "1zJY7LbsIwFAV_xZr1BdlOQM1dAn8ReRGCGywZpw1PCeXfq4bt0YzmvHmg"
        assertRoundTrips(text, sizePx = 400)
    }

    // T8-I2: the sample above is 58 characters. Real export codes run 278-800 characters (the
    // module's own MAX_CODE_BYTES cap), which is a meaningfully denser matrix than 58 chars ever
    // produces — that density is what a phone camera actually has to resolve. Round-trip genuine
    // watchOS-produced codes instead of only the synthetic short sample.

    @Test fun `a real single-code export round-trips`() {
        val text = readFixtureLine("watch-export-single.txt")
        assertTrue("fixture should be a real single export code, was ${text.length} chars", text.length in 200..StandaloneExport.MAX_CODE_BYTES)
        assertRoundTrips(text, sizePx = 500)
    }

    @Test fun `a real multi-code export sequence page round-trips`() {
        val text = readFixtureLine("watch-export-sequence.txt", line = 0)
        assertTrue("fixture should be near the cap, was ${text.length} chars", text.length in 700..StandaloneExport.MAX_CODE_BYTES)
        assertRoundTrips(text, sizePx = 500)
    }

    @Test fun `a generated code at the MAX_CODE_BYTES cap round-trips`() {
        val entries = entriesFillingOneChunkNearCap()
        val codes = StandaloneExport.codes(entries)
        assertEquals("expected the fixture builder to still fit one chunk", 1, codes.size)
        val text = codes[0]
        assertTrue("expected a near-cap code, was ${text.length} chars", text.length in 700..StandaloneExport.MAX_CODE_BYTES)
        assertRoundTrips(text, sizePx = 500)
    }

    private fun assertRoundTrips(text: String, sizePx: Int) {
        val bmp = QrBitmap.render(text, sizePx)
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bmp.width, bmp.height, px)))).text
        assertEquals(text, decoded)
    }

    /** Adds entries one at a time until the next one would overflow MAX_CODE_BYTES into a second
     *  chunk, then returns the list just before that — the largest single-chunk export the encoder
     *  will produce, i.e. as close to the cap as `StandaloneExport` itself gets. */
    private fun entriesFillingOneChunkNearCap(): List<LoggedFood> {
        val entries = mutableListOf<LoggedFood>()
        var i = 0
        while (i < 500) {
            val food = WatchFood("Fixture Food Item $i, A Reasonably Long Descriptive USDA-Style Name", 200.0 + i, 10.0, 5.0, 30.0, 2.0)
            entries += LoggedFood(food, 150.0 + i, FoodLogMeal.values()[i % 4], 1_700_000_000L + i)
            if (StandaloneExport.codes(entries).size > 1) {
                entries.removeAt(entries.lastIndex)
                return entries
            }
            i++
        }
        error("could not build a near-cap chunk within 500 entries")
    }

    private fun readFixtureLine(name: String, line: Int = 0): String {
        val dir = System.getProperty("liftkitFixturesDir") ?: error("liftkitFixturesDir system property not set (see wear/build.gradle.kts)")
        return File(dir, name).readLines()[line]
    }
}
