package com.dugcanlift.liftwear
import com.dugcanlift.liftkit.FoodLogMeal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [defaultMeal] must agree with watchOS's `FoodLogMeal.forHour` at every hour of the day.
 * Settled 2026-09-13: the two watches match each other, not LIFT Android's phone-side
 * `mealForHour`, which uses 11/15/21.
 */
class MealDefaultTest {

    /** watchOS's `FoodLogMeal.forHour`, transcribed. If that changes, this changes with it. */
    private fun watchOS(hour: Int): FoodLogMeal = when (hour) {
        in 5..10 -> FoodLogMeal.BREAKFAST
        in 11..15 -> FoodLogMeal.LUNCH
        in 16..21 -> FoodLogMeal.DINNER
        else -> FoodLogMeal.SNACK
    }

    @Test fun `every hour of the day matches watchOS`() {
        for (hour in 0..23) {
            assertEquals("hour $hour", watchOS(hour), defaultMeal(hour))
        }
    }

    @Test fun `the middle of each meal is the obvious one`() {
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(7))
        assertEquals(FoodLogMeal.LUNCH, defaultMeal(12))
        assertEquals(FoodLogMeal.DINNER, defaultMeal(18))
        assertEquals(FoodLogMeal.SNACK, defaultMeal(23))
    }

    @Test fun `the small hours are a snack, not breakfast`() {
        // The change from LIFT Android's thresholds that a user is most likely to notice:
        // food logged at 3am was breakfast here until 2026-09-13.
        assertEquals(FoodLogMeal.SNACK, defaultMeal(0))
        assertEquals(FoodLogMeal.SNACK, defaultMeal(4))
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(5))
    }

    @Test fun `each boundary falls on the watchOS hour, not the phone's`() {
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(10))
        assertEquals(FoodLogMeal.LUNCH, defaultMeal(11))

        // 15:00 was Dinner under the old 11/15/21 thresholds; watchOS keeps it Lunch.
        assertEquals(FoodLogMeal.LUNCH, defaultMeal(15))
        assertEquals(FoodLogMeal.DINNER, defaultMeal(16))

        // 21:00 was Snack under the old thresholds; watchOS keeps it Dinner.
        assertEquals(FoodLogMeal.DINNER, defaultMeal(21))
        assertEquals(FoodLogMeal.SNACK, defaultMeal(22))
    }
}
