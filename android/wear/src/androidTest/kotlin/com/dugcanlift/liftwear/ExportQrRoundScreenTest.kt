package com.dugcanlift.liftwear

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dugcanlift.liftkit.*
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.hypot

/**
 * The failure the unit suite cannot see. On a round watch the bezel hides everything outside the
 * display's inscribed circle; a QR drawn to the full square loses its corner finder patterns and no
 * scanner can acquire it. The export screen sizes the code to the inscribed square for exactly this
 * reason, and this test checks that claim the only way it can be checked: render the real screen on
 * a round device, photograph the display, black out what a bezel would hide, and try to read it.
 *
 * Runs on a round Wear OS emulator (`.github/workflows/qr-nightly.yml`, or locally against the
 * `Wear_Round` AVD from docs/DEVICE-TESTING.md). Screenshots land in the app's external files dir
 * under `qr-test/` so a failure can be looked at.
 */
@RunWith(AndroidJUnit4::class)
class ExportQrRoundScreenTest {
    @get:Rule val compose = createComposeRule()

    // Logged "today": the log keeps a bounded window of recent entries, so a fixed date would
    // silently age out and the screen would show "Nothing logged yet." instead of a code.
    private val now = System.currentTimeMillis() / 1000
    private val fixture = listOf(
        LoggedFood(WatchFood("Rolled oats", 389.0, 16.9, 6.9, 66.3, 10.6), 80.0, FoodLogMeal.BREAKFAST, now - 6 * 3600),
        LoggedFood(WatchFood("Whole milk", 61.0, 3.2, 3.3, 4.8, 0.0), 250.0, FoodLogMeal.BREAKFAST, now - 6 * 3600 + 60),
        LoggedFood(WatchFood("Chicken breast, roasted", 165.0, 31.0, 3.6, 0.0, 0.0), 180.0, FoodLogMeal.LUNCH, now - 3600),
    )

    @Test fun exportCodeSurvivesTheBezel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(
            "This test only means something on a round display; run it on a round Wear OS AVD",
            context.resources.configuration.isScreenRound,
        )

        // The screen encodes whatever the log holds when it opens, stamped with the current time,
        // so the exact string cannot be predicted here; the decoded payload is checked instead.
        val log = StandaloneFoodLog(PrefsLogStorage(context))
        log.clear()
        fixture.forEach(log::append)
        assertEquals("the log did not keep the fixture", fixture.size, log.entries.size)
        assertEquals("fixture must fit one code", 1, StandaloneExport.codes(fixture).size)

        // Internal storage: the Wear images report "nosdcard" and external files dirs come back null.
        // Pull with: adb shell run-as com.dugcanlift.liftwear.debug tar -C files -c qr-test | tar -x
        val outDir = File(context.filesDir, "qr-test").apply { mkdirs() }
        compose.setContent { LiftWearTheme { ExportScreen(log) {} } }
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithContentDescription("Code 1 of 1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            instrumentation.uiAutomation.takeScreenshot()?.let { save(it, File(outDir, "timeout.png")) }
            fail("The export screen never showed its code. Semantics:\n" + compose.onRoot(useUnmergedTree = true).printToString())
        }
        compose.waitForIdle()
        Thread.sleep(500) // the composition is done; give the display a couple of frames to show it

        val raw = instrumentation.uiAutomation.takeScreenshot() ?: fail("uiAutomation.takeScreenshot returned null").let { error("") }
        val masked = maskToInscribedCircle(raw)
        save(raw, File(outDir, "display.png"))
        save(masked, File(outDir, "display-behind-bezel.png"))

        val text = try {
            decodeQr(masked)
        } catch (e: NotFoundException) {
            fail("No QR code could be read from the display once the bezel is accounted for; see ${outDir}/display-behind-bezel.png")
            error("")
        }

        // 1z = deflated JSON, 1u = plain JSON, both base64url; see StandaloneExport.envelope.
        assertTrue("unexpected envelope prefix in '${text.take(4)}'", text.startsWith("1z") || text.startsWith("1u"))
        val body = CompactEncoding.base64UrlDecode(text.substring(2)) ?: fail("envelope is not base64url").let { error("") }
        val json = if (text[1] == 'z') CompactEncoding.inflateRaw(body) ?: fail("envelope did not inflate").let { error("") } else body
        val payload = Json.parseToJsonElement(String(json)).jsonObject
        assertEquals(fixture.size, payload.getValue("e").jsonArray.size)
        assertEquals(
            fixture.map { it.food.name }.distinct(),
            payload.getValue("fd").jsonArray.map { it.jsonArray[0].jsonPrimitive.content },
        )
        assertEquals(listOf(1, 1), payload.getValue("p").jsonArray.map { it.jsonPrimitive.content.toInt() })
    }

    /**
     * The control: the same code drawn to the full square, which is exactly the bug the export
     * screen sizes against. If the bezel mask were too lenient this would decode too, and the test
     * above would be passing for the wrong reason.
     */
    @Test fun fullBleedCodeDoesNotSurviveTheBezel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("run on a round Wear OS AVD", context.resources.configuration.isScreenRound)
        val code = StandaloneExport.codes(fixture).single()
        val side = context.resources.displayMetrics.widthPixels
        val fullBleed = QrBitmap.render(code, side)

        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Image(fullBleed.asImageBitmap(), contentDescription = "full-bleed code",
                      modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            }
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription("full-bleed code", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        Thread.sleep(500)

        val raw = instrumentation.uiAutomation.takeScreenshot() ?: fail("uiAutomation.takeScreenshot returned null").let { error("") }
        val outDir = File(context.filesDir, "qr-test").apply { mkdirs() }
        save(raw, File(outDir, "control-full-bleed.png"))
        val masked = maskToInscribedCircle(raw)
        save(masked, File(outDir, "control-full-bleed-behind-bezel.png"))
        // Sanity: the bitmap itself is a perfectly good code, so a failure below is the bezel's
        // doing. Padded, because a code flush with the image edge has no quiet zone and zxing
        // rejects it for that reason alone. (Not the screenshot: a round emulator already clips
        // its own framebuffer to the circle, so the screenshot is bezel-clipped before the mask.)
        assertEquals(code, decodeQr(padWhite(fullBleed, side / 8)))
        try {
            val text = decodeQr(padWhite(masked, side / 8))
            fail("A full-bleed code decoded through the bezel mask, so the mask proves nothing: $text")
        } catch (expected: NotFoundException) {
            // the finder patterns are under the bezel, as they would be on a real watch
        }
    }

    private fun padWhite(source: Bitmap, pad: Int): Bitmap {
        val out = Bitmap.createBitmap(source.width + 2 * pad, source.height + 2 * pad, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(out).drawBitmap(source, pad.toFloat(), pad.toFloat(), null)
        return out
    }

    /**
     * Everything outside the largest circle that fits the display is what a round bezel hides. A
     * round emulator already blacks that out in its own framebuffer; the mask is for a device that
     * reports round but captures the full square.
     */
    private fun maskToInscribedCircle(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val cx = out.width / 2.0
        val cy = out.height / 2.0
        val r = minOf(out.width, out.height) / 2.0
        val row = IntArray(out.width)
        for (y in 0 until out.height) {
            out.getPixels(row, 0, out.width, 0, y, out.width, 1)
            for (x in 0 until out.width) if (hypot(x + 0.5 - cx, y + 0.5 - cy) > r) row[x] = android.graphics.Color.BLACK
            out.setPixels(row, 0, out.width, 0, y, out.width, 1)
        }
        return out
    }

    private fun decodeQr(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        // No TRY_HARDER: a real phone camera gets one clean look at a watch face, and a code that
        // only decodes with the exhaustive search is a code people will struggle to scan.
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
