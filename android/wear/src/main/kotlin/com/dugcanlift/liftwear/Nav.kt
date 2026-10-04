package com.dugcanlift.liftwear
import androidx.compose.runtime.*
import com.dugcanlift.liftkit.*

object Routes { const val HOME = "home"; const val SEARCH = "search"; const val AMOUNT = "amount"; const val MEAL = "meal"; const val EXPORT = "export"; const val PHONE = "phone" }

/**
 * The in-progress log entry, held above the nav graph so back/forward do not lose it.
 *
 * [unit] lives here rather than inside `AmountScreen` so the chosen unit survives logging a food,
 * matching watchOS's `session.servingUnit`. As screen-local state it reset to grams on every
 * entry, so someone logging in ounces dialled "5" expecting 5 oz and got 5 g on their third food.
 */
class Draft {
    var food by mutableStateOf<WatchFood?>(null)
    var grams by mutableStateOf(100.0)
    var unit by mutableStateOf(ServingUnit.GRAMS)
}
