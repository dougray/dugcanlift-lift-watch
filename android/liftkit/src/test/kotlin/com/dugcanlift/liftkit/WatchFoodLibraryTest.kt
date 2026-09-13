package com.dugcanlift.liftkit
import org.junit.Assert.*
import org.junit.Test
class WatchFoodLibraryTest {
    @Test fun `parses the row shape name category kcal protein fat carbs fibre`() {
        val lib = WatchFoodLibrary.parse("""{"categories":["Poultry"],"foods":[["Chicken breast, roasted",0,165,31.02,3.57,0,0]]}""")
        assertEquals(1, lib.count)
        assertEquals(WatchFood("Chicken breast, roasted", 165.0, 31.02, 3.57, 0.0, 0.0), lib.search("chicken").single())
    }
    @Test fun `the bundled library has every USDA row`() = assertEquals(7793, WatchFoodLibrary.bundled().count)
    @Test fun `search is a case-insensitive substring capped at the limit`() {
        val lib = WatchFoodLibrary.bundled()
        val hits = lib.search("CHICKEN BREAST")
        assertTrue(hits.isNotEmpty()); assertTrue(hits.size <= 30)
        assertTrue(hits.all { it.name.lowercase().contains("chicken breast") })
    }
    @Test fun `a blank query returns nothing rather than everything`() {
        assertTrue(WatchFoodLibrary.bundled().search("   ").isEmpty())
    }
    @Test fun `results keep file order`() {
        val lib = WatchFoodLibrary.parse("""{"categories":[],"foods":[["B egg",0,1,0,0,0,0],["A egg",0,1,0,0,0,0]]}""")
        assertEquals(listOf("B egg", "A egg"), lib.search("egg").map { it.name })
    }
    @Test fun `an ounce is 28,3495 grams`() = assertEquals(28.3495, ServingUnit.OUNCES.toGrams(1.0), 1e-9)
}
