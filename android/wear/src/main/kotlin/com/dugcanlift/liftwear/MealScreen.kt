package com.dugcanlift.liftwear
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.*
import java.time.LocalTime

/** Thresholds match LIFT Android's mealForHour and LiftCore's MealType.forHour. */
fun defaultMeal(hour: Int): FoodLogMeal = when {
    hour < 11 -> FoodLogMeal.BREAKFAST; hour < 15 -> FoodLogMeal.LUNCH; hour < 21 -> FoodLogMeal.DINNER; else -> FoodLogMeal.SNACK
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
