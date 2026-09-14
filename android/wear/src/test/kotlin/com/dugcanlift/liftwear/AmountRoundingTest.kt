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
        // 3.5 oz would store 99.22333085 g raw, behind a screen reading "3.5 oz".
        // (3.25 oz was the old example; the 0.5 oz step adopted from watchOS no longer reaches it.)
        val shown = amountShown(ServingUnit.OUNCES, 3.5)
        assertEquals(3.5, shown, 0.0)
        assertEquals(99.2, gramsFor(ServingUnit.OUNCES, shown), 0.0)
    }

    @Test fun `a grams to ounces to grams round trip returns the same portion`() {
        var grams = 100.0
        grams = gramsFor(ServingUnit.OUNCES, ServingUnit.OUNCES.fromGrams(grams))
        assertEquals("switching to ounces must not move the portion", 100.0, grams, 0.0)
        grams = gramsFor(ServingUnit.GRAMS, ServingUnit.GRAMS.fromGrams(grams))
        assertEquals(100.0, grams, 0.0)
    }

    @Test fun `driving the ounces path to its ceiling never stores more than the 2000 g cap`() {
        // 2000 g is 70.5479... oz, which 0.1-oz snapping rounds *down* to 70.5 oz -- so the ounce
        // path now tops out at 1998.6 g rather than reaching 2000 g exactly. That is the cost of
        // watchOS's one-decimal display, and it errs on the safe side of the cap. Grams mode still
        // reaches 2000 exactly. (watchOS's own ounce ceiling is a 70 oz literal = 1984.5 g, so this
        // is still the closer of the two.)
        val (shown, grams) = clampedAmount(ServingUnit.OUNCES, 1_000.0)
        assertEquals(70.5, shown, 0.0)
        assertTrue("stored grams must never exceed the 2000 g cap", grams <= 2000.0)
        assertEquals(1998.6, grams, 0.0)
    }

    // M6: watchOS's library flow floors at 5 g (FoodSearchView.swift's `Stepper(..., in: 5...1000, ...)`).
    // Wear's floor used to be 0, so dialling down (or a stray "-" tap) could log a 0 g / 0 kcal entry
    // that still spent a slot in the 200-entry cap and a row in an exported QR code.
    @Test fun `a zero-gram request is floored at 5 g, not logged as 0`() {
        val (shown, grams) = clampedAmount(ServingUnit.GRAMS, 0.0)
        assertEquals(5.0, shown, 0.0)
        assertEquals(5.0, grams, 0.0)
    }

    @Test fun `driving the ounces path down never stores less than the 5 g floor`() {
        // The 5 g floor is 0.1764 oz; 0.1-oz snapping rounds that up to 0.2 oz, which converts to
        // 5.7 g -- never below the 5 g floor, only ever at or above it.
        val (shown, grams) = clampedAmount(ServingUnit.OUNCES, -1.0)
        assertEquals(0.2, shown, 0.0)
        assertTrue("stored grams must never be below the 5 g floor", grams >= 5.0)
        assertEquals(5.7, grams, 0.0)
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

/**
 * The ounce mode's parameters, settled 2026-09-13 to match watchOS's `FoodAmountEntryView`.
 * Ounces stay on Wear (they are the only amount flow it has), but every knob that watchOS also
 * has now reads the same on both.
 */
class OunceParityTest {

    @Test fun `one ounce is watchOS's constant, not a shorter one`() {
        assertEquals(28.3495231, ServingUnit.OUNCES.toGrams(1.0), 1e-9)
    }

    @Test fun `the display shows one decimal and prints whole numbers bare`() {
        assertEquals("4", amountLabel(ServingUnit.OUNCES, 4.0))
        assertEquals("4.5", amountLabel(ServingUnit.OUNCES, 4.5))
        assertEquals("120", amountLabel(ServingUnit.GRAMS, 120.0))
    }

    @Test fun `the half-ounce step lands only on values the display can show exactly`() {
        // With a 0.5 oz step every reachable amount is .0 or .5, so one decimal place is not a
        // rounding of the truth -- it is the truth.
        var oz = 0.5
        while (oz <= 12.0) {
            assertEquals("$oz oz", oz, amountShown(ServingUnit.OUNCES, oz), 0.0)
            oz += 0.5
        }
    }

    @Test fun `the chosen unit survives logging a food`() {
        // watchOS keeps it in session.servingUnit; Wear keeps it on the Draft. It used to reset to
        // grams on every entry, so the third food someone logged in ounces became grams silently.
        val draft = Draft()
        assertEquals(ServingUnit.GRAMS, draft.unit)
        draft.unit = ServingUnit.OUNCES
        draft.food = null          // what logging a food does
        draft.grams = 100.0
        assertEquals("the unit is not part of the entry being cleared", ServingUnit.OUNCES, draft.unit)
    }
}
