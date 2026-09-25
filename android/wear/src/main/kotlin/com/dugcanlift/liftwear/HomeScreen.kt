package com.dugcanlift.liftwear
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dugcanlift.liftkit.StandaloneFoodLog
import com.dugcanlift.liftkit.displayName
import com.dugcanlift.liftkit.totalSetCount

/**
 * wear-compose-navigation keeps Home composed across the single hop to Export, so a
 * remember { } count only ever reflects the first composition. The NavHost provides each
 * destination its own back-stack-entry-scoped LocalLifecycleOwner, which re-fires ON_RESUME
 * when swiping back from Export — observe that instead of relying on recomposition.
 *
 * The same observer is what refreshes **today's plan**: it arrives over the radio while this screen
 * is showing, and a plan that landed a second ago must be on the screen the next time it is looked at.
 */
@Composable fun HomeScreen(
    log: StandaloneFoodLog,
    linkStore: PhoneLinkStore,
    session: SessionController,
    onLog: () -> Unit,
    onExport: () -> Unit,
    onPhone: () -> Unit,
    onStartPlan: () -> Unit,
    onResume: () -> Unit,
    onFreeWorkout: () -> Unit,
) {
    var count by remember { mutableStateOf(log.entries.size) }
    // A discarded quarantine is a second corruption that "first wins" refused to keep — surfaced
    // here so a user or a future support path can tell something was dropped rather than the loss
    // being invisible.
    var corrupted by remember { mutableStateOf(log.quarantineDiscardedCount) }
    var plan by remember { mutableStateOf(session.todaysPlan()) }
    val draft by session.draft.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                count = log.entries.size; corrupted = log.quarantineDiscardedCount
                plan = session.todaysPlan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("LIFT") } }

            // A workout in progress owns the top of the screen: it is the thing the lifter came back
            // to the app for, and it survived the process being killed to be here.
            val inProgress = draft
            if (inProgress != null) {
                item {
                    Chip(
                        onClick = onResume,
                        label = { Text("Resume workout", maxLines = 1) },
                        secondaryLabel = { Text("${inProgress.name} · ${inProgress.completedSetCount} sets") },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // Today's plan, when the phone has pushed one for today. Everything below is
                // unchanged: a day with no plan is the app this has always been.
                val today = plan
                if (today != null) {
                    item {
                        Chip(
                            onClick = onStartPlan,
                            label = { Text(today.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = {
                                Text("${today.exercises.size} exercises · ${today.totalSetCount} sets")
                            },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { PlanFooter(today.source.displayName) }
                }
                item {
                    Chip(
                        onClick = onFreeWorkout,
                        label = { Text("Start workout") },
                        colors = if (today == null) ChipDefaults.primaryChipColors()
                                 else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Asking costs one message and answers the question the session screen needs answered
                // before the lifter starts. Only offered when there is a phone to ask.
                if (linkStore.isPaired) {
                    item {
                        Chip(
                            onClick = { session.requestPlan() },
                            label = { Text(if (plan == null) "Get today's plan" else "Refresh plan") },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { Chip(onClick = onLog, label = { Text("Log food") }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            // "Nothing logged yet" means exactly that: since 2026-09-13 nothing is hidden by age,
            // so a non-empty log always has something to export.
            item { Chip(onClick = onExport, label = { Text("Export logged foods") },
                        secondaryLabel = { Text(when {
                            count > 0 -> "$count to export"
                            else -> "Nothing logged yet"
                        }) },
                        colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            // The QR export above stays the watch's own route out, paired or not: pairing adds a
            // way in for the day's workout, it does not replace the way out for the food log.
            item { Chip(onClick = onPhone, label = { Text("Phone") },
                        secondaryLabel = { Text(linkStore.pairedName ?: "Not paired") },
                        colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            if (corrupted > 0) item { Text("$corrupted corrupted log${if (corrupted == 1) "" else "s"} could not be recovered", color = DclColors.Muted) }
        }
    }
}

@Composable private fun PlanFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = DclColors.Accent2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}
