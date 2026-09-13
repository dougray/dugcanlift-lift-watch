package com.dugcanlift.liftwear
import androidx.compose.runtime.*
import com.dugcanlift.liftkit.*

object Routes { const val HOME = "home"; const val SEARCH = "search"; const val AMOUNT = "amount"; const val MEAL = "meal"; const val EXPORT = "export" }

/** The in-progress log entry, held above the nav graph so back/forward do not lose it. */
class Draft { var food by mutableStateOf<WatchFood?>(null); var grams by mutableStateOf(100.0) }
