package com.dugcanlift.liftwear
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.ServingUnit
import kotlin.math.roundToInt

/** Single source of truth for the maximum a portion may be: 2000 g. Ounces is derived from this by
 *  conversion rather than a separate literal, so toggling units near the cap never silently drops grams
 *  (70 oz would truncate to 1984.68 g if clamped as its own rounded limit). */
private const val MAX_GRAMS = 2000.0

/** Grams (or ounces) via the rotary input and +/-; kcal for the chosen amount updates live. */
@Composable fun AmountScreen(draft: Draft, onNext: () -> Unit) {
    val food = draft.food ?: return
    var unit by remember { mutableStateOf(ServingUnit.GRAMS) }
    var amount by remember { mutableStateOf(unit.fromGrams(draft.grams)) }
    val step = if (unit == ServingUnit.GRAMS) 5.0 else 0.25
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun set(a: Double) { amount = a.coerceIn(0.0, unit.fromGrams(MAX_GRAMS)); draft.grams = unit.toGrams(amount) }
    Scaffold(timeText = { TimeText() }) {
        Column(Modifier.fillMaxSize().padding(12.dp)
                .onRotaryScrollEvent { set(amount + if (it.verticalScrollPixels > 0) step else -step); true }
                .focusRequester(focus).focusable(),
               horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Extra horizontal padding: above the vertical centre of a round display the chord is
            // narrower than the full diameter, so long USDA names need more inset than a square screen would.
            Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis, color = DclColors.Muted,
                 modifier = Modifier.padding(horizontal = 20.dp))
            Text("${if (unit == ServingUnit.GRAMS) amount.roundToInt().toString() else "%.2f".format(amount)} ${if (unit == ServingUnit.GRAMS) "g" else "oz"}", style = MaterialTheme.typography.display2)
            Text("${(food.kcal * draft.grams / 100.0).roundToInt()} kcal", color = DclColors.Text)
            Row {
                CompactButton(onClick = { set(amount - step) }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                CompactButton(onClick = { set(amount + step) }) { Text("+") }
                Spacer(Modifier.width(8.dp))
                CompactChip(onClick = { unit = if (unit == ServingUnit.GRAMS) ServingUnit.OUNCES else ServingUnit.GRAMS; amount = unit.fromGrams(draft.grams) },
                            label = { Text(if (unit == ServingUnit.GRAMS) "oz" else "g") })
            }
            Spacer(Modifier.height(8.dp))
            Chip(onClick = onNext, label = { Text("Next") }, colors = ChipDefaults.primaryChipColors())
        }
    }
}
