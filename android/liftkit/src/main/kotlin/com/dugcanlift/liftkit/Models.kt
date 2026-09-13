package com.dugcanlift.liftkit
import kotlinx.serialization.Serializable

@Serializable enum class FoodLogMeal { BREAKFAST, LUNCH, DINNER, SNACK }

enum class ServingUnit(private val gramsPerUnit: Double) {
    GRAMS(1.0), OUNCES(28.3495);
    fun toGrams(amount: Double) = amount * gramsPerUnit
    fun fromGrams(grams: Double) = grams / gramsPerUnit
}

/** One row of the bundled library. Macros are PER 100 g. */
@Serializable data class WatchFood(val name: String, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double, val fibre: Double)

/** A logged portion. `loggedAtEpochSeconds` is seconds, not millis: that is what the wire carries. */
@Serializable data class LoggedFood(val food: WatchFood, val grams: Double, val meal: FoodLogMeal, val loggedAtEpochSeconds: Long)
