package com.dugcanlift.liftkit
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StandaloneExportTest {
    private val chicken = WatchFood("Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.02, 3.57, 0.0, 0.0)
    private val oats = WatchFood("Oats, whole grain, rolled, old fashioned", 379.0, 13.15, 6.52, 67.7, 10.1)
    private val z = 1757500800L
    private fun e(food: WatchFood, grams: Double, meal: FoodLogMeal, at: Long) = LoggedFood(food, grams, meal, at)

    @Test fun `meal indices match SHARE-FORMAT`() {
        assertEquals(listOf(0, 1, 2, 3), FoodLogMeal.values().map(StandaloneExport::mealIndex))
    }
    @Test fun `empty log produces no codes`() = assertTrue(StandaloneExport.codes(emptyList(), z).isEmpty())
    @Test fun `single code carries every entry and a deduplicated food dictionary`() {
        val codes = StandaloneExport.codes(listOf(e(chicken, 50.0, FoodLogMeal.BREAKFAST, 1757486400), e(oats, 87.0, FoodLogMeal.LUNCH, 1757491800), e(chicken, 124.0, FoodLogMeal.DINNER, 1757497200)), z)
        assertEquals(1, codes.size)
        val p = TestDecode.payload(codes[0])
        assertEquals(2, p.getValue("fd").jsonArray.size)
        assertEquals(3, p.getValue("e").jsonArray.size)
    }
    @Test fun `entry tuple is index grams meal timestamp`() {
        val p = TestDecode.payload(StandaloneExport.codes(listOf(e(oats, 87.0, FoodLogMeal.LUNCH, 1757491800)), z)[0])
        assertEquals(listOf(0, 87, 1, 1757491800L).map { JsonPrimitive(it) }, p.getValue("e").jsonArray[0].jsonArray.toList())
    }
    @Test fun `food dictionary row is name then macros per 100 g`() {
        val row = TestDecode.payload(StandaloneExport.codes(listOf(e(oats, 87.0, FoodLogMeal.LUNCH, 1757491800)), z)[0]).getValue("fd").jsonArray[0].jsonArray
        assertEquals("Oats, whole grain, rolled, old fashioned", row[0].jsonPrimitive.content)
        assertEquals(listOf(379.0, 13.15, 6.52, 67.7, 10.1), row.drop(1).map { it.jsonPrimitive.double })
    }
    @Test fun `keys are in sorted order e fd p v z and whole doubles print as integers`() {
        val code = StandaloneExport.codes(listOf(e(oats, 100.0, FoodLogMeal.SNACK, 1757491800)), z)[0]
        val text = String(CompactEncoding.inflateRaw(CompactEncoding.base64UrlDecode(code.substring(2))!!)!!)
        assertTrue(text, text.startsWith("{\"e\":"))
        assertEquals(listOf("e", "fd", "p", "v", "z"), TestDecode.payload(code).keys.toList())
        assertTrue(text, text.contains("[0,100,3,1757491800]"))   // 100, not 100.0
        assertTrue(text, text.contains(",379,"))
    }
    @Test fun `logs longer than the cap split across numbered codes and every entry survives`() {
        val entries = (0 until 120).map { i -> e(if (i % 2 == 0) chicken else oats, 50.0 + i, FoodLogMeal.values()[i % 4], 1757486400L + i * 1800) }
        val codes = StandaloneExport.codes(entries, z)
        assertTrue(codes.size > 1)
        val total = codes.size
        codes.forEachIndexed { i, c -> assertEquals(listOf(i + 1, total), TestDecode.payload(c).getValue("p").jsonArray.map { it.jsonPrimitive.int }) }
        assertEquals(120, codes.sumOf { TestDecode.payload(it).getValue("e").jsonArray.size })
        codes.forEach { assertTrue("${it.length} > 800", it.length <= StandaloneExport.MAX_CODE_BYTES) }
    }
    @Test fun `all codes in a sequence share an export timestamp`() {
        val entries = (0 until 120).map { i -> e(if (i % 2 == 0) chicken else oats, 50.0 + i, FoodLogMeal.values()[i % 4], 1757486400L + i * 1800) }
        assertEquals(1, StandaloneExport.codes(entries, z).map { TestDecode.payload(it).getValue("z").jsonPrimitive.long }.toSet().size)
    }
    @Test fun `a single oversized entry produces exactly one code rather than hanging or vanishing`() {
        val rnd = java.util.Random(42)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val huge = WatchFood(String(CharArray(1200) { alphabet[rnd.nextInt(alphabet.length)] }), 1.0, 1.0, 1.0, 1.0, 1.0)
        val codes = StandaloneExport.codes(listOf(e(huge, 1.0, FoodLogMeal.LUNCH, 1757491800), e(oats, 1.0, FoodLogMeal.LUNCH, 1757491801)), z)
        assertEquals(2, codes.size)
        assertEquals(1, TestDecode.payload(codes[0]).getValue("e").jsonArray.size)
    }
    @Test fun `every code matches the envelope`() {
        StandaloneExport.codes(listOf(e(oats, 1.0, FoodLogMeal.LUNCH, 1757491800)), z).forEach { assertTrue(it, Regex("^1[zu][A-Za-z0-9_-]+$").matches(it)) }
    }
    @Test fun `uncompressed fallback is prefixed u and decodes back to the original JSON`() {
        val json = """{"e":[[0,1,1,1]],"fd":[["a",1,1,1,1,1]],"p":[1,1],"v":1,"z":1}""".toByteArray()
        val code = StandaloneExport.envelope(json, deflate = { null })
        assertTrue(code.startsWith("1u"))
        assertArrayEquals(json, CompactEncoding.base64UrlDecode(code.substring(2)))
    }
}
