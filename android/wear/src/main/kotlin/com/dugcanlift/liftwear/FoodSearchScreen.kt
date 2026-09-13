package com.dugcanlift.liftwear
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Text
import com.dugcanlift.liftkit.WatchFood
import com.dugcanlift.liftkit.WatchFoodLibrary
import kotlinx.coroutines.Deferred

@Composable fun FoodSearchScreen(library: Deferred<WatchFoodLibrary>, onPick: (WatchFood) -> Unit) {
    Text("Food search")
}
