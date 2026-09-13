package com.dugcanlift.liftwear
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Text
import com.dugcanlift.liftkit.StandaloneFoodLog

@Composable fun MealScreen(draft: Draft, log: StandaloneFoodLog, onDone: () -> Unit) {
    Text("Meal")
}
