package com.dugcanlift.liftwear
import com.dugcanlift.liftkit.FoodLogMeal
import org.junit.Assert.assertEquals
import org.junit.Test

class MealDefaultTest {
    @Test fun `hour of day picks the meal the phone apps would`() {
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(7)); assertEquals(FoodLogMeal.LUNCH, defaultMeal(12))
        assertEquals(FoodLogMeal.DINNER, defaultMeal(18)); assertEquals(FoodLogMeal.SNACK, defaultMeal(22))
    }

    @Test fun `boundary hours distinguish the correct thresholds from the brief's wrong ones`() {
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(0))
        assertEquals(FoodLogMeal.BREAKFAST, defaultMeal(10))
        assertEquals(FoodLogMeal.LUNCH, defaultMeal(11))
        assertEquals(FoodLogMeal.LUNCH, defaultMeal(14))
        assertEquals(FoodLogMeal.DINNER, defaultMeal(15))
        assertEquals(FoodLogMeal.DINNER, defaultMeal(20))
        assertEquals(FoodLogMeal.SNACK, defaultMeal(21))
        assertEquals(FoodLogMeal.SNACK, defaultMeal(23))
    }
}
