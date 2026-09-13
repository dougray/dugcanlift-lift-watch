package com.dugcanlift.liftwear
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.StandaloneFoodLog

@Composable fun HomeScreen(log: StandaloneFoodLog, onLog: () -> Unit, onExport: () -> Unit) {
    var count by remember { mutableStateOf(log.entries.size) }
    LaunchedEffect(Unit) { count = log.entries.size }
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
