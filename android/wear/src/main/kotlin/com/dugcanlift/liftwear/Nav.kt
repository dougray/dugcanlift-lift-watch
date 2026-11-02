package com.dugcanlift.liftwear
import androidx.compose.runtime.*
import com.dugcanlift.liftkit.*

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val AMOUNT = "amount"
    const val MEAL = "meal"; const val EXPORT = "export"; const val PHONE = "phone"
    /** The guided session's own pages. [LOG_SET] takes the draft exercise's id. */
    const val SESSION = "session"; const val LOG_SET = "logset"; const val REST = "rest"
    const val EXERCISES = "exercises"; const val ADD_EXERCISE = "addexercise"; const val SUMMARY = "summary"
    fun logSet(exerciseId: String) = "$LOG_SET/$exerciseId"
    const val LOG_SET_ROUTE = "$LOG_SET/{exerciseId}"
}

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
