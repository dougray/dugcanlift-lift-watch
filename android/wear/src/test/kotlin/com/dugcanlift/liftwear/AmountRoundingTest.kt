package com.dugcanlift.liftwear
import com.dugcanlift.liftkit.FoodLogMeal
import com.dugcanlift.liftkit.LoggedFood
import com.dugcanlift.liftkit.ServingUnit
import com.dugcanlift.liftkit.StandaloneExport
import com.dugcanlift.liftkit.WatchFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** I4: the screen showed a rounded number and logged the raw conversion behind it. */
class AmountRoundingTest {
    @Test fun `grams are recorded at the whole-gram precision the screen shows`() {
        val shown = amountShown(ServingUnit.GRAMS, 97.4)
        assertEquals(97.0, shown, 0.0)
        assertEquals(97.0, gramsFor(ServingUnit.GRAMS, shown), 0.0)
    }

    @Test fun `an ounce amount records 0_1 g not the raw conversion`() {
        // 3.25 oz used to store 92.135875 g behind a screen reading "92 g".
        val shown = amountShown(ServingUnit.OUNCES, 3.25)
        assertEquals(3.25, shown, 0.0)
        assertEquals(92.1, gramsFor(ServingUnit.OUNCES, shown), 0.0)
    }

    @Test fun `a grams to ounces to grams round trip returns the same portion`() {
        var grams = 100.0
        grams = gramsFor(ServingUnit.OUNCES, ServingUnit.OUNCES.fromGrams(grams))
        assertEquals("switching to ounces must not move the portion", 100.0, grams, 0.0)
        grams = gramsFor(ServingUnit.GRAMS, ServingUnit.GRAMS.fromGrams(grams))
        assertEquals(100.0, grams, 0.0)
    }

    @Test fun `driving the ounces path to its ceiling never stores more than the 2000 g cap`() {
        // 2000 g is 70.5477... oz; 0.01-oz snapping used to round the clamped ceiling up to 70.55 oz,
        // which converts to 2000.1 g -- overshooting the documented cap by 0.1 g.
        val (shown, grams) = clampedAmount(ServingUnit.OUNCES, 1_000.0)
        assertEquals(70.55, shown, 0.0)
        assertTrue("stored grams must never exceed the 2000 g cap", grams <= 2000.0)
        assertEquals(2000.0, grams, 0.0)
    }

    @Test fun `ounce entries no longer double the number of codes to scan`() {
        val food = WatchFood("Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.02, 3.57, 0.0, 0.0)
        val rounded = (0 until 200).map {
            LoggedFood(food, gramsFor(ServingUnit.OUNCES, amountShown(ServingUnit.OUNCES, 3.25)), FoodLogMeal.LUNCH, 1_757_486_400L + it)
        }
        val raw = (0 until 200).map {
            LoggedFood(food, ServingUnit.OUNCES.toGrams(3.25), FoodLogMeal.LUNCH, 1_757_486_400L + it)
        }
        assertTrue("rounded ounce entries should not need more codes than the raw ones did",
                   StandaloneExport.codes(rounded).size <= StandaloneExport.codes(raw).size)
    }
}
