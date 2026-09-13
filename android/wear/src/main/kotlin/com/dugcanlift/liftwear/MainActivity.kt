package com.dugcanlift.liftwear
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.navigation.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.get(this)
        setContent {
            LiftWearTheme {
                val nav = rememberSwipeDismissableNavController()
                val draft = remember { Draft() }
                SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(container.log, onLog = { nav.navigate(Routes.SEARCH) }, onExport = { nav.navigate(Routes.EXPORT) }) }
                    composable(Routes.SEARCH) { FoodSearchScreen(container.library) { food -> draft.food = food; nav.navigate(Routes.AMOUNT) } }
                    composable(Routes.AMOUNT) { AmountScreen(draft) { nav.navigate(Routes.MEAL) } }
                    composable(Routes.MEAL) { MealScreen(draft, container.log) { nav.popBackStack(Routes.HOME, inclusive = false) } }
                    composable(Routes.EXPORT) { ExportScreen(container.log) { nav.popBackStack() } }
                }
            }
        }
    }
}
