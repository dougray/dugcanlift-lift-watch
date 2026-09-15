package com.dugcanlift.liftkit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TodayTotalsTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun entry(grams: Double, at: Instant) = LoggedFood(
        food = WatchFood("Oats, rolled, dry", kcal = 379.0, protein = 13.2, fat = 6.5, carbs = 67.7, fibre = 10.1),
        grams = grams,
        meal = FoodLogMeal.BREAKFAST,
        loggedAtEpochSeconds = at.epochSecond
    )

    @Test fun `totals scale by grams`() {
        val noon = at("2026-09-13T12:00")
        val totals = TodayTotals.totals(listOf(entry(50.0, noon)), noon, zone)
        assertEquals(189.5, totals.kcal, 0.001)
        assertEquals(6.6, totals.protein, 0.001)
        assertEquals(33.85, totals.carbs, 0.001)
        assertEquals(3.25, totals.fat, 0.001)
    }

    @Test fun `late evening entry counts as today`() {
        // 23:30 local is the same local day as noon. A UTC boundary files this
        // as tomorrow and the evening's food vanishes off the face.
        val noon = at("2026-09-13T12:00")
        val totals = TodayTotals.totals(listOf(entry(100.0, at("2026-09-13T23:30"))), noon, zone)
        assertEquals(379.0, totals.kcal, 0.001)
    }

    @Test fun `yesterday is excluded`() {
        val noon = at("2026-09-13T12:00")
        val totals = TodayTotals.totals(listOf(entry(100.0, at("2026-09-12T23:30"))), noon, zone)
        assertEquals(NutritionTotals.ZERO, totals)
    }

    @Test fun `dst fall back day folds whole day`() {
        // 2026-11-01 is a 25-hour day in America/New_York.
        val early = at("2026-11-01T01:00")
        val totals = TodayTotals.totals(listOf(entry(100.0, at("2026-11-01T23:30"))), early, zone)
        assertEquals(379.0, totals.kcal, 0.001)
    }

    @Test fun `no entries is zero`() {
        assertEquals(NutritionTotals.ZERO, TodayTotals.totals(emptyList(), at("2026-09-13T12:00"), zone))
    }

    @Test fun `goal defaults match the ios ones`() {
        assertEquals(1748.0, Goals.FALLBACK.calories, 0.0)
        assertEquals(160.0, Goals.FALLBACK.protein, 0.0)
        assertEquals(167.0, Goals.FALLBACK.carbs, 0.0)
        assertEquals(49.0, Goals.FALLBACK.fat, 0.0)
        assertEquals(10_000.0, Goals.FALLBACK.steps, 0.0)
    }
}
