package com.dugcanlift.liftwear
import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
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
    // null means "the 634 KB library has not finished parsing", which is not the same as "no match".
    var hits by remember { mutableStateOf<List<WatchFood>?>(emptyList()) }
    var inputUnavailable by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            query = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(KEY)?.toString().orEmpty()
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { hits = emptyList(); return@LaunchedEffect }
        hits = null
        hits = library.await().search(query)
    }
    val results = hits
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Chip(onClick = {
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(KEY).setLabel("Food").build()))
                // The app's only text entry, and an implicit intent: a Wear build without the system
                // remote-input activity — which is exactly the de-Googled audience the no-GMS policy
                // serves — would otherwise throw here and take the app down with nothing logged.
                try { launcher.launch(intent) } catch (_: ActivityNotFoundException) { inputUnavailable = true }
            }, label = { Text(if (query.isBlank()) "Search foods" else query) }, modifier = Modifier.fillMaxWidth()) }
            if (inputUnavailable) item { Text("This watch has no voice or keyboard input for apps.", color = DclColors.Muted) }
            if (query.isNotBlank() && results == null) item { Text("Loading the food library…", color = DclColors.Muted) }
            if (query.isNotBlank() && results != null && results.isEmpty()) item { Text("Nothing found for that.", color = DclColors.Muted) }
            items(results.orEmpty()) { food ->
                Chip(onClick = { onPick(food) }, label = { Text(food.name, maxLines = 2) },
                     secondaryLabel = { Text("${food.kcal.toInt()} kcal / 100 g") },
                     colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
private const val KEY = "query"
