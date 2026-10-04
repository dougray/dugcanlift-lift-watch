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
                    composable(Routes.HOME) {
                        HomeScreen(
                            log = container.log,
                            linkStore = container.linkStore,
                            onLog = { nav.navigate(Routes.SEARCH) },
                            onExport = { nav.navigate(Routes.EXPORT) },
                            onPhone = { nav.navigate(Routes.PHONE) },
                        )
                    }
                    composable(Routes.SEARCH) {
                        // Entering search is where a new entry begins: reset any amount left over from a
                        // flow that was abandoned with a back-swipe before it reached Meal/append.
                        LaunchedEffect(Unit) { draft.grams = 100.0 }
                        FoodSearchScreen(container.library) { food -> draft.food = food; nav.navigate(Routes.AMOUNT) }
                    }
                    composable(Routes.AMOUNT) { AmountScreen(draft) { nav.navigate(Routes.MEAL) } }
                    composable(Routes.MEAL) { MealScreen(draft, container.log) { nav.popBackStack(Routes.HOME, inclusive = false) } }
                    composable(Routes.EXPORT) { ExportScreen(container.log) { nav.popBackStack() } }
                    composable(Routes.PHONE) { PhoneLinkScreen(container.phoneLink, container.linkStore) { nav.popBackStack() } }
                }
            }
        }
    }
}
