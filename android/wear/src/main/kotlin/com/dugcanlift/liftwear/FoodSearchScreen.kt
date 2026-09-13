package com.dugcanlift.liftwear
import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.*
import androidx.wear.compose.material.*
import androidx.wear.input.RemoteInputIntentHelper
import com.dugcanlift.liftkit.*
import kotlinx.coroutines.Deferred

@Composable fun FoodSearchScreen(library: Deferred<WatchFoodLibrary>, onPick: (WatchFood) -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<WatchFood>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            query = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(KEY)?.toString().orEmpty()
        }
    }
    LaunchedEffect(query) { hits = if (query.isBlank()) emptyList() else library.await().search(query) }
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Chip(onClick = {
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(KEY).setLabel("Food").build()))
                launcher.launch(intent)
            }, label = { Text(if (query.isBlank()) "Search foods" else query) }, modifier = Modifier.fillMaxWidth()) }
            if (query.isNotBlank() && hits.isEmpty()) item { Text("Nothing found for that.", color = DclColors.Muted) }
            items(hits) { food ->
                Chip(onClick = { onPick(food) }, label = { Text(food.name, maxLines = 2) },
                     secondaryLabel = { Text("${food.kcal.toInt()} kcal / 100 g") },
                     colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
private const val KEY = "query"
