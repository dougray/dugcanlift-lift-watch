package com.dugcanlift.liftwear

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.*
import com.dugcanlift.liftkit.link.LogSide
import kotlinx.coroutines.delay

/**
 * The guided session on the wrist: what to lift, which set it is, the prescription in digits big
 * enough to read mid-set, what was actually done last time, and the heart rate.
 *
 * Everything it says comes from `:liftkit`'s value types — [GuidedSession.positionText],
 * `PrescribedSet.headline`, `LastPerformed.summary` — so what this file does is lay them out. The one
 * rule it must not break is the one those types exist to keep: **a blank prescription is a dash, never
 * a zero.**
 *
 * Round display: a [ScalingLazyColumn] insets its own ends, and the horizontal padding is the extra a
 * chord above the centre needs, the same allowance `AmountScreen` makes for long food names.
 */
@Composable
fun SessionScreen(
    session: SessionController,
    heart: HeartRateRecorder?,
    onLogSet: (String) -> Unit,
    onRest: () -> Unit,
    onExercises: () -> Unit,
    onSummary: () -> Unit,
) {
    val draft by session.draft.collectAsState()
    val guided by session.guided.collectAsState()
    val unit by session.unit.collectAsState()
    val bpm = heart?.bpm?.collectAsState()?.value

    val current = draft ?: return
    val plan = guided

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            val exercise = plan?.currentExercise
            if (plan != null && exercise != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = plan.plan.name.uppercase(),
                            style = MaterialTheme.typography.caption3,
                            color = DclColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(plan.positionText, style = MaterialTheme.typography.caption1)
                    }
                }
                item {
                    Text(
                        text = exercise.displayName,
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
                item {
                    // The prescription, in the size the spec draws it. "6 reps" is a set with no
                    // weight prescribed and "—" is a set that prescribes nothing at all; neither is
                    // ever a zero.
                    Text(
                        text = plan.currentPrescription?.headline(unit, plan.currentSide) ?: "—",
                        style = MaterialTheme.typography.display2,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                plan.currentPrescription?.rpe?.let { rpe ->
                    item { SessionCaption("RPE ${PlanFormat.rpe(rpe)}") }
                }
                exercise.lastPerformed?.summary(unit)?.let { last ->
                    item { SessionCaption("last: $last") }
                }
                exercise.note?.takeIf { it.isNotBlank() }?.let { note ->
                    item {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.caption2,
                            color = DclColors.Accent2,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        )
                    }
                }
                // No number until a sample has actually landed: a watch on a table produces none, and
                // a zero pretending to be a reading is worse than a line that is not there.
                if (bpm != null) item { SessionCaption("$bpm bpm") }
                item {
                    val exerciseId = session.guidedExerciseId()
                    Chip(
                        onClick = { exerciseId?.let(onLogSet) },
                        enabled = exerciseId != null,
                        label = { Text("Log set") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item { ListHeader { Text(if (plan == null) current.name else "Plan done") } }
                if (plan != null) {
                    item { SessionCaption("${plan.completedSetCount} of ${plan.plan.totalSetCount} sets") }
                    item { SessionCaption("Finish below, or keep lifting off plan.") }
                } else {
                    item { SessionCaption("${current.completedSetCount} sets logged") }
                }
            }

            item { SecondaryChip("Sets", onExercises) }
            item { SecondaryChip("Rest", onRest) }
            item { SecondaryChip("Finish", onSummary) }
        }
    }
}

/**
 * Weight, reps, RPE and a side, on the rotary crown — the lifter is holding a bar.
 *
 * The fields start at [SetEntrySeed], which is where the whole pre-fill rule lives and is tested. What
 * this screen adds is that **no weight stays no weight**: the field has a real dash state, and a set
 * logged there travels with no `weightKg` at all rather than a zero somebody has to interpret.
 */
@Composable
fun LogSetScreen(session: SessionController, exerciseId: String, onLogged: () -> Unit) {
    val draft by session.draft.collectAsState()
    val unit by session.unit.collectAsState()
    val exercise = draft?.exercise(exerciseId) ?: return
    val prescription = session.prescription(exerciseId)
    val step = SetEntrySeed.step(unit)

    // Seeded once per exercise-and-set-count, so logging a set and coming back re-seeds from the set
    // just logged rather than from where the screen was left.
    val seed = remember(exerciseId, exercise.sets.size, unit) {
        SetEntrySeed.of(
            unit = unit,
            prescription = prescription,
            previousSet = exercise.sets.lastOrNull(),
            lastPerformed = session.guided.value
                ?.takeIf { session.guidedExerciseId() == exerciseId }
                ?.currentExercise?.lastPerformed,
            side = session.suggestedSide(exerciseId),
        )
    }

    var shownWeight by remember(seed) { mutableStateOf(seed.weightKg?.let { unit.fromKilograms(it) }) }
    var reps by remember(seed) { mutableStateOf(seed.reps) }
    var rpe by remember(seed) { mutableStateOf(seed.rpe) }
    var side by remember(seed) { mutableStateOf(seed.side) }

    // Whether there is a side to ask about at all. A bench press screen is exactly what it was.
    val showsSide = seed.side != null || side != null

    fun nudgeWeight(direction: Int) {
        val currentValue = shownWeight
        shownWeight = when {
            currentValue == null -> if (direction > 0) step else null   // a dash steps up to one plate
            else -> (currentValue + direction * step).let { if (it < step) null else it }
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(exerciseId) { focus.requestFocus() }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { nudgeWeight(if (it.verticalScrollPixels > 0) 1 else -1); true }
                .focusRequester(focus)
                .focusable()
        ) {
            item {
                Text(
                    text = exercise.displayName,
                    style = MaterialTheme.typography.caption1,
                    color = DclColors.Muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                )
            }
            prescription?.let { item { SessionCaption("asked: ${it.summary(unit, side)}") } }
            exercise.sets.lastOrNull()?.let { item { SessionCaption("before: ${it.display(unit)}") } }

            item {
                // A dash, not a zero: a set with no weight is a real set, and this is the field that
                // has to be able to say so.
                Stepper(
                    label = "Weight",
                    value = shownWeight?.let { "${PlanFormat.weight(unit.toKilograms(it), unit)} ${unit.abbreviation}" }
                        ?: "—",
                    onDown = { nudgeWeight(-1) },
                    onUp = { nudgeWeight(1) },
                )
            }
            item {
                Stepper(
                    label = "Reps",
                    value = "$reps",
                    onDown = { reps = (reps - 1).coerceAtLeast(1) },
                    onUp = { reps = (reps + 1).coerceAtMost(50) },
                )
            }
            item {
                Stepper(
                    label = "RPE",
                    value = rpe?.let(PlanFormat::rpe) ?: "—",
                    onDown = { rpe = rpe?.let { (it - 0.5).takeIf { v -> v >= 6.0 } } },
                    onUp = { rpe = ((rpe ?: 5.5) + 0.5).coerceAtMost(10.0) },
                )
            }
            if (showsSide) item { SideControl(side) { side = it } }

            item {
                Chip(
                    onClick = {
                        session.logSet(
                            exerciseId = exerciseId,
                            // Null becomes 0.0 in the draft, which `toLoggedSet` sends as absent --
                            // `DraftSet.weightKg` is the unit-free store and 0 is its "not weighed".
                            weightKg = shownWeight?.let(unit::toKilograms) ?: 0.0,
                            reps = reps,
                            rpe = rpe,
                            side = side,
                        )
                        onLogged()
                    },
                    label = { Text("Log set") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CompactChip(
                    onClick = { session.setUnit(unit.other) },
                    label = { Text("Show ${unit.other.abbreviation}") },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

/**
 * Two buttons and a clear, exactly as the phone's set row does it: a segmented picker cannot express
 * "neither", and a set done on both limbs at once has no side at all. Tapping the highlighted side
 * clears it back to both, so nothing here is a one-way door.
 */
@Composable
private fun SideControl(side: LogSide?, onChange: (LogSide?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("Side", style = MaterialTheme.typography.caption3, color = DclColors.Muted)
        Row(modifier = Modifier.fillMaxWidth()) {
            LogSide.entries.forEach { option ->
                val selected = side == option
                CompactChip(
                    onClick = { onChange(if (selected) null else option) },
                    label = {
                        Text(
                            option.shortLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** Rest, with the buzz at zero that is the whole reason not to have to look at it. */
@Composable
fun RestScreen(session: SessionController, onBack: () -> Unit) {
    val rest by session.rest.collectAsState()
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var buzzed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    // Derived from the start instant, so this is right even if the screen was off for a minute.
    LaunchedEffect(rest, now) {
        if (!rest.isRunning) { buzzed = false; return@LaunchedEffect }
        if (rest.hasFinished(now)) {
            if (!buzzed) { buzzRestOver(context); buzzed = true }
        } else buzzed = false
    }

    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = rest.progress(now).toFloat(),
                indicatorColor = DclColors.Accent,
                trackColor = DclColors.Rule,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
            ) {
                Text("REST", style = MaterialTheme.typography.caption3, color = DclColors.Muted)
                Text(
                    text = RestTimer.format(
                        rest.remainingSeconds(now) ?: rest.intervalSeconds.toDouble()
                    ),
                    style = MaterialTheme.typography.display1,
                )
                Spacer(Modifier.height(4.dp))
                if (rest.isRunning) {
                    CompactChip(
                        onClick = { session.skipRest(); onBack() },
                        label = { Text("Skip") },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                } else {
                    CompactChip(
                        onClick = { session.startRest() },
                        label = { Text("Start") },
                        colors = ChipDefaults.primaryChipColors(),
                    )
                }
            }
        }
    }
}

/**
 * Every exercise of the session, with what was last done on it. Opening one out of order moves the
 * guided session with it, so the prescription on the next screen belongs to the exercise the lifter
 * actually opened.
 */
@Composable
fun ExercisesScreen(
    session: SessionController,
    onLogSet: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val draft by session.draft.collectAsState()
    val unit by session.unit.collectAsState()
    val current = draft ?: return

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
            items(current.exercises) { exercise ->
                Chip(
                    onClick = { session.focus(exercise.id); onLogSet(exercise.id) },
                    label = { Text(exercise.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(exercise.sets.lastOrNull()?.display(unit) ?: "No sets yet")
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SecondaryChip("Add exercise", onAdd) }
        }
    }
}

/**
 * A short, fixed list, which is the right shape for a wrist: the full reference database is on the
 * phone, and scrolling thousands of rows is not a feature. The same six lifts LIFT for Apple Watch
 * offers, so the two wrists agree about what "off plan" can start from.
 */
@Composable
fun AddExerciseScreen(session: SessionController, onAdded: () -> Unit) {
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Exercise") } }
            items(COMMON_LIFTS) { lift ->
                Chip(
                    onClick = {
                        session.addExercise(lift.name, lift.equipment, lift.refId)
                        onAdded()
                    },
                    label = { Text(lift.name) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class CommonLift(val refId: String, val name: String, val equipment: String?)

private val COMMON_LIFTS = listOf(
    CommonLift("bench-barbell", "Bench Press", "barbell"),
    CommonLift("squat-barbell", "Squat", "barbell"),
    CommonLift("deadlift-barbell", "Deadlift", "barbell"),
    CommonLift("ohp-barbell", "Overhead Press", "barbell"),
    CommonLift("row-barbell", "Bent Over Row", "barbell"),
    CommonLift("pullup-bodyweight", "Pull Up", "bodyweight"),
)

/** Sets, volume, whether the phone has it, and Finish. */
@Composable
fun SessionSummaryScreen(session: SessionController, linkStatus: String, onFinished: () -> Unit) {
    val draft by session.draft.collectAsState()
    val unit by session.unit.collectAsState()
    val pending by session.pending.collectAsState()
    val current = draft ?: return

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Summary") } }
            item { SessionCaption("${current.completedSetCount} sets") }
            item {
                SessionCaption(
                    "${Math.round(unit.fromKilograms(current.totalVolumeKg))} ${unit.abbreviation} lifted"
                )
            }
            item { SessionCaption(linkStatus) }
            if (pending > 0) {
                item { SessionCaption("$pending session${if (pending == 1) "" else "s"} waiting for your phone") }
            }
            item {
                Chip(
                    onClick = { session.finish(); onFinished() },
                    label = { Text("Finish workout") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---- small shared pieces ----------------------------------------------------------------------

@Composable
private fun SessionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = DclColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
    )
}

@Composable
private fun SecondaryChip(label: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A label, its value, and a button each side — the shape a crown-less finger needs. */
@Composable
private fun Stepper(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactButton(onClick = onDown) { Text("−") }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.caption3, color = DclColors.Muted)
            Text(value, style = MaterialTheme.typography.title3, maxLines = 1)
        }
        CompactButton(onClick = onUp) { Text("+") }
    }
}
