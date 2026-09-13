package com.dugcanlift.liftwear
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dugcanlift.liftkit.StandaloneFoodLog

/**
 * wear-compose-navigation keeps Home composed across the single hop to Export, so a
 * remember { } count only ever reflects the first composition. The NavHost provides each
 * destination its own back-stack-entry-scoped LocalLifecycleOwner, which re-fires ON_RESUME
 * when swiping back from Export — observe that instead of relying on recomposition.
 */
@Composable fun HomeScreen(log: StandaloneFoodLog, onLog: () -> Unit, onExport: () -> Unit) {
    var count by remember { mutableStateOf(log.entries.size) }
    var expired by remember { mutableStateOf(log.expiredCount) }
    // A discarded quarantine is a second corruption that "first wins" refused to keep — surfaced
    // here, next to `expired`, so a user or a future support path can tell something was dropped
    // rather than the loss being invisible.
    var corrupted by remember { mutableStateOf(log.quarantineDiscardedCount) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                count = log.entries.size; expired = log.expiredCount; corrupted = log.quarantineDiscardedCount
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("LIFT") } }
            item { Chip(onClick = onLog, label = { Text("Log food") }, colors = ChipDefaults.primaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            // "Nothing logged yet" now means exactly that: entries that aged past the 60-day window
            // are still in storage, and saying a user who logged for weeks never logged anything is
            // the empty state the spec asks to distinguish from Export's.
            item { Chip(onClick = onExport, label = { Text("Export logged foods") },
                        secondaryLabel = { Text(when {
                            count > 0 -> "$count to export"
                            expired > 0 -> "$expired expired, none to export"
                            else -> "Nothing logged yet"
                        }) },
                        colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            if (corrupted > 0) item { Text("$corrupted corrupted log${if (corrupted == 1) "" else "s"} could not be recovered", color = DclColors.Muted) }
        }
    }
}
