package com.dugcanlift.liftkit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The window rule against `fixtures/heart-rate-per-set.json`, a fixture **worked by hand** from the
 * build plan's rule and checked into this repo and into lift-ios, where the Swift port reads the same
 * file. It is the only test here that is about more than this port agreeing with itself.
 *
 * **Never regenerate or edit the fixture from this code.** If a case fails, [HeartRatePerSet] is
 * wrong. The checksum below is there so an edit to the file fails loudly instead of quietly moving
 * the two ports apart.
 */
class HeartRatePerSetTest {
    private val bytes: ByteArray by lazy {
        javaClass.getResourceAsStream("/fixtures/heart-rate-per-set.json")!!.readBytes()
    }

    private val fixture: JsonObject by lazy { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }

    @Test fun `the fixture is the hand-worked file, byte for byte`() {
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("868e271eb75389c20db9e8c89ec98b1daa97930e94cbde5bcc587e568945fac6", sha)
    }

    @Test fun `every case in the fixture comes out as it was worked by hand`() {
        val cases = fixture.getValue("cases").jsonArray
        assertTrue("the fixture should hold its nine cases", cases.size >= 9)
        cases.forEach { element ->
            val case = element.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val samples = case.getValue("samples").jsonArray.map { pair ->
                val (at, bpm) = pair.jsonArray.map { it.jsonPrimitive.double }
                HeartRatePerSet.Sample(at, bpm)
            }
            val sets = case.getValue("sets").jsonArray.map { set ->
                val o = set.jsonObject
                HeartRatePerSet.Tick(
                    id = o.getValue("id").jsonPrimitive.content,
                    atSeconds = o["tick"]?.takeIf { it != JsonNull }?.jsonPrimitive?.doubleOrNull,
                    durationSeconds = o["durationSec"]?.takeIf { it != JsonNull }?.jsonPrimitive?.doubleOrNull,
                )
            }
            val expected = case.getValue("expected").jsonObject.mapValues { (_, v) ->
                if (v == JsonNull) null else v.jsonPrimitive.int
            }
            val actual = HeartRatePerSet.figures(case.getValue("sessionStart").jsonPrimitive.double, samples, sets)
            assertEquals(name, expected, actual)
        }
    }

    // ---- the pieces the fixture leans on ---------------------------------------------------------

    @Test fun `halves round away from zero, not to even`() {
        assertEquals(142, HeartRatePerSet.wholeBpm(141.5))
        assertEquals(143, HeartRatePerSet.wholeBpm(142.5))
        assertEquals(99, HeartRatePerSet.wholeBpm(99.49))
        assertEquals(-2, HeartRatePerSet.wholeBpm(-1.5))
    }

    @Test fun `a window with no reading is no figure, never a zero`() {
        val figures = HeartRatePerSet.figures(
            sessionStart = 0.0,
            samples = listOf(HeartRatePerSet.Sample(40.0, 0.0), HeartRatePerSet.Sample(45.0, Double.NaN)),
            sets = listOf(HeartRatePerSet.Tick("a", 30.0)),
        )
        assertNull(figures.getValue("a"))
    }

    @Test fun `a set with no tick has no window`() {
        val windows = HeartRatePerSet.windows(0.0, listOf(HeartRatePerSet.Tick("a", 60.0), HeartRatePerSet.Tick("b", null)))
        assertEquals(setOf("a"), windows.keys)
        assertEquals(HeartRatePerSet.Window(0.0, 80.0), windows.getValue("a"))
    }
}
