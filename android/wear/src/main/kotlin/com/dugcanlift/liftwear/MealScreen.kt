package com.dugcanlift.liftwear
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.*
import java.time.LocalTime

/**
 * Which meal a given hour suggests. **Matches watchOS's `FoodLogMeal.forHour` exactly**
 * (5/11/16/22), settled 2026-09-13 so the two watches agree.
 *
 * This deliberately differs from LIFT Android's own `mealForHour` (11/15/21), which these
 * thresholds used to copy. Two consequences, both intended:
 *
 * - Before 05:00 is a **snack, not breakfast**. Food logged at 3am is far more likely to be a
 *   night shift or a bad night than the first meal of the day, and watchOS has always said so.
 * - Lunch runs to 16:00 and dinner to 22:00, an hour later than the phone on each.
 *
 * So the same food logged at 15:30 lands in Lunch on the watch and Dinner on LIFT Android.
 * The suggestion is only ever a pre-selection -- every screen that uses it lets the meal be
 * changed before anything is written -- but if the phone is ever brought into line, this is the
 * function it should be brought into line *with*.
 */
fun defaultMeal(hour: Int): FoodLogMeal = when {
    hour < 5 -> FoodLogMeal.SNACK
    hour < 11 -> FoodLogMeal.BREAKFAST
    hour < 16 -> FoodLogMeal.LUNCH
    hour < 22 -> FoodLogMeal.DINNER
    else -> FoodLogMeal.SNACK
}

@Composable fun MealScreen(draft: Draft, log: StandaloneFoodLog, onDone: () -> Unit) {
    val food = draft.food ?: return
    val suggested = remember { defaultMeal(LocalTime.now().hour) }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Which meal?") } }
            items(FoodLogMeal.values().toList()) { meal ->
                Chip(onClick = {
                    log.append(LoggedFood(food, draft.grams, meal, System.currentTimeMillis() / 1000))
                    draft.food = null; draft.grams = 100.0
                    onDone()
                }, label = { Text(meal.name.lowercase().replaceFirstChar { it.uppercase() }) },
                   colors = if (meal == suggested) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                   modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
