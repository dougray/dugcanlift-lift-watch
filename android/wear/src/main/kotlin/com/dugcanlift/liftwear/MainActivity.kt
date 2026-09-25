package com.dugcanlift.liftwear
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.get(this)
        setContent {
            LiftWearTheme {
                val nav = rememberSwipeDismissableNavController()
                val draft = remember { Draft() }
                KeepTheLinkUpWhilePaired(container)
                AskForHeartRateOnce(container)
                SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            log = container.log,
                            linkStore = container.linkStore,
                            session = container.session,
                            onLog = { nav.navigate(Routes.SEARCH) },
                            onExport = { nav.navigate(Routes.EXPORT) },
                            onPhone = { nav.navigate(Routes.PHONE) },
                            onStartPlan = { if (container.session.startPlanned()) nav.navigate(Routes.SESSION) },
                            onResume = {
                                // A free-entry session has no Now page to resume onto, so it resumes
                                // where its content is.
                                nav.navigate(
                                    if (container.session.guided.value != null) Routes.SESSION
                                    else Routes.EXERCISES
                                )
                            },
                            onFreeWorkout = {
                                container.session.startFreeWorkout()
                                nav.navigate(Routes.EXERCISES)
                            },
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

                    // ---- the guided session ----------------------------------------------------
                    composable(Routes.SESSION) {
                        SessionScreen(
                            session = container.session,
                            heart = container.heartRate,
                            onLogSet = { id -> nav.navigate(Routes.logSet(id)) },
                            onRest = { nav.navigate(Routes.REST) },
                            onExercises = { nav.navigate(Routes.EXERCISES) },
                            onSummary = { nav.navigate(Routes.SUMMARY) },
                        )
                    }
                    composable(
                        Routes.LOG_SET_ROUTE,
                        arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
                    ) { entry ->
                        val exerciseId = entry.arguments?.getString("exerciseId").orEmpty()
                        LogSetScreen(container.session, exerciseId) {
                            // Straight to the rest that just started, which is the next thing the
                            // lifter is actually doing.
                            nav.popBackStack()
                            nav.navigate(Routes.REST)
                        }
                    }
                    composable(Routes.REST) { RestScreen(container.session) { nav.popBackStack() } }
                    composable(Routes.EXERCISES) {
                        ExercisesScreen(
                            session = container.session,
                            onLogSet = { id -> nav.navigate(Routes.logSet(id)) },
                            onAdd = { nav.navigate(Routes.ADD_EXERCISE) },
                        )
                    }
                    composable(Routes.ADD_EXERCISE) {
                        AddExerciseScreen(container.session) { nav.popBackStack() }
                    }
                    composable(Routes.SUMMARY) {
                        SessionSummaryScreen(
                            session = container.session,
                            linkStatus = if (container.phoneLink.isLinked) "Phone connected"
                                         else "Offline — queued",
                        ) { nav.popBackStack(Routes.HOME, inclusive = false) }
                    }
                }
            }
        }
    }
}

/**
 * The radio, for as long as the app is open and a phone is remembered.
 *
 * `docs/LINK-PROTOCOL.md` has always described this — "the watch advertises while the Phone screen is
 * open, **or while the app is in the foreground with a phone already remembered**" — and until the
 * guided session existed only the first half was implemented, because only that screen needed it. A
 * session needs the link on every screen: that is how a set reaches a phone on the bench, and how
 * "Get today's plan" has anything to ask.
 *
 * Still nothing when LIFT is closed, and still nothing when no phone is paired. Those are the two
 * promises, and they are what makes this an honest battery knob rather than a hidden cost.
 */
@Composable
private fun KeepTheLinkUpWhilePaired(container: AppContainer) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START ->
                    if (container.linkStore.isPaired && container.phoneLink.hasBluetoothPermission) {
                        container.phoneLink.start()
                        // Whatever the phone has not acknowledged is offered again on every
                        // reconnect, because a send is not a receipt.
                        container.session.flushOutbox()
                    }
                Lifecycle.Event.ON_STOP -> container.phoneLink.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Asks for the heart-rate sensor once, the first time the app opens after this feature existed.
 *
 * Asked here rather than at the moment a workout starts, because a system dialog between "Start
 * workout" and the first prescription is exactly the interruption a guided session is meant to remove.
 * A refusal is final and costs the bpm line and nothing else — `HeartRateRecorder.start` simply
 * returns.
 */
@Composable
private fun AskForHeartRateOnce(container: AppContainer) {
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { asked = true }
    LaunchedEffect(Unit) {
        if (!asked && !container.heartRate.hasPermission) {
            asked = true
            launcher.launch(HeartRateRecorder.runtimePermissions)
        }
    }
}
