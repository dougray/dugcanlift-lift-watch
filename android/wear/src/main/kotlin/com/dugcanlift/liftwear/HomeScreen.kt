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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) count = log.entries.size }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("LIFT") } }
            item { Chip(onClick = onLog, label = { Text("Log food") }, colors = ChipDefaults.primaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            item { Chip(onClick = onExport, label = { Text("Export logged foods") },
                        secondaryLabel = { Text(if (count == 0) "Nothing logged yet" else "$count to export") },
                        colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
        }
    }
}
