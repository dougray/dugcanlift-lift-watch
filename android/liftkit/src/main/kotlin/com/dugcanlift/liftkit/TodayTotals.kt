package com.dugcanlift.liftkit

import java.time.Instant
import java.time.ZoneId

/**
 * Calories and the three macros the face shows. Fibre is tracked by [WatchFood]
 * but deliberately absent: the face has room for three bars.
 *
 * Twin of apple/LiftKit's `NutritionTotals`. Keep the two in step.
 */
data class NutritionTotals(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    companion object {
        val ZERO = NutritionTotals(0.0, 0.0, 0.0, 0.0)
    }
}

object TodayTotals {

    /**
     * Folds every entry logged on the same **local** day as [reference].
     *
     * Day membership is a `LocalDate` comparison in [zone], never arithmetic on
     * the epoch seconds. A UTC boundary misfiles an evening log as tomorrow,
     * and 86,400-second arithmetic repeats or skips a day across a DST change.
     */
    fun totals(
        entries: List<LoggedFood>,
        reference: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): NutritionTotals {
        val today = reference.atZone(zone).toLocalDate()
        return entries
            .filter { Instant.ofEpochSecond(it.loggedAtEpochSeconds).atZone(zone).toLocalDate() == today }
            .fold(NutritionTotals.ZERO) { running, entry ->
                val per100g = entry.grams / 100
                NutritionTotals(
                    kcal = running.kcal + entry.food.kcal * per100g,
                    protein = running.protein + entry.food.protein * per100g,
                    carbs = running.carbs + entry.food.carbs * per100g,
                    fat = running.fat + entry.food.fat * per100g
                )
            }
    }
}

/**
 * The daily targets the face measures against. Defaults match LIFT iOS's own
 * `@AppStorage` values so a watch that has never been told otherwise shows
 * real targets rather than zeroes.
 */
data class Goals(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val steps: Double
) {
    companion object {
        val FALLBACK = Goals(
            calories = 1748.0,
            protein = 160.0,
            carbs = 167.0,
            fat = 49.0,
            steps = 10_000.0
        )
    }
}
