package com.dugcanlift.liftkit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** The only tests here that prove interoperability with a different implementation (watchOS). */
class StandaloneExportParityTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/fixtures/$name")!!.bufferedReader().readText().trim().lines()

    @Test fun `the Kotlin encoder produces the same content as the watchOS single code`() {
        val web = TestDecode.payload(fixture("watch-export-single.txt").single())
        val chicken = WatchFood("Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.02, 3.57, 0.0, 0.0)
        val oats = WatchFood("Oats, whole grain, rolled, old fashioned", 379.0, 13.15, 6.52, 67.7, 10.1)
        val mine = TestDecode.payload(StandaloneExport.codes(listOf(
            LoggedFood(chicken, 50.0, FoodLogMeal.BREAKFAST, 1757486400),
            LoggedFood(oats, 87.0, FoodLogMeal.LUNCH, 1757491800),
            LoggedFood(chicken, 124.0, FoodLogMeal.DINNER, 1757497200)), 1757500800).single())

        assertEquals(web.keys.toList(), mine.keys.toList())
        assertEquals(web.getValue("e"), mine.getValue("e"))
        assertEquals(web.getValue("p"), mine.getValue("p"))
        assertEquals(web.getValue("z"), mine.getValue("z"))
        val wfd = web.getValue("fd").jsonArray; val mfd = mine.getValue("fd").jsonArray
        assertEquals(wfd.size, mfd.size)
        wfd.zip(mfd).forEach { (w, m) ->
            assertEquals(w.jsonArray[0], m.jsonArray[0])
            w.jsonArray.drop(1).zip(m.jsonArray.drop(1)).forEach { (a, b) -> assertEquals(a.jsonPrimitive.double, b.jsonPrimitive.double, 1e-9) }
        }
    }
    @Test fun `print the Kotlin single code for the site fixture`() {
        // Not an assertion: copy this output to dugcanlift-site/lift/fixtures/wear-export-single.txt.
        val chicken = WatchFood("Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.02, 3.57, 0.0, 0.0)
        val oats = WatchFood("Oats, whole grain, rolled, old fashioned", 379.0, 13.15, 6.52, 67.7, 10.1)
        println("WEAR_FIXTURE " + StandaloneExport.codes(listOf(
            LoggedFood(chicken, 50.0, FoodLogMeal.BREAKFAST, 1757486400), LoggedFood(oats, 87.0, FoodLogMeal.LUNCH, 1757491800),
            LoggedFood(chicken, 124.0, FoodLogMeal.DINNER, 1757497200)), 1757500800).single())
    }
    @Test fun `the Kotlin decoder reads the watchOS two-code sequence completely`() {
        val codes = fixture("watch-export-sequence.txt")
        val payloads = codes.map(TestDecode::payload)
        assertEquals(listOf(listOf(1, 2), listOf(2, 2)), payloads.map { it.getValue("p").jsonArray.map { p -> p.jsonPrimitive.int } })
        assertEquals(1, payloads.map { it.getValue("z") }.toSet().size)
        assertTrue(payloads.sumOf { it.getValue("e").jsonArray.size } > 19)
    }
}
