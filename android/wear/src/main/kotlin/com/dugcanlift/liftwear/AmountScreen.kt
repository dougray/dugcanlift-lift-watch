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
import kotlin.math.round
import kotlin.math.roundToInt

/** Single source of truth for the maximum a portion may be: 2000 g. Ounces is derived from this by
 *  conversion rather than a separate literal, so toggling units near the cap never silently drops grams
 *  (70 oz would truncate to 1984.68 g if clamped as its own rounded limit). */
private const val MAX_GRAMS = 2000.0

/**
 * The precision the screen shows, which is also the precision that gets logged. Grams display as
 * whole numbers, so grams are stored whole; ounces display to 0.01 oz, and 0.01 oz is 0.28 g, so
 * 0.1 g records the shown value with room to spare. Storing the raw conversion instead put
 * 92.135875 g in the record and on the wire behind a screen reading "92 g" — and `num()` only
 * shortens whole doubles, so every such entry cost ~9 characters of a 800-byte code rather than 2.
 */
internal fun amountShown(unit: ServingUnit, amount: Double): Double =
    if (unit == ServingUnit.GRAMS) round(amount) else round(amount * 100.0) / 100.0

/** The grams to record for an amount the user set in [unit]. Never finer than [amountShown]. */
internal fun gramsFor(unit: ServingUnit, amount: Double): Double =
    if (unit == ServingUnit.GRAMS) round(amount) else round(unit.toGrams(amount) * 10.0) / 10.0

/** Grams (or ounces) via the rotary input and +/-; kcal for the chosen amount updates live. */
@Composable fun AmountScreen(draft: Draft, onNext: () -> Unit) {
    val food = draft.food ?: return
    var unit by remember { mutableStateOf(ServingUnit.GRAMS) }
    var amount by remember { mutableStateOf(unit.fromGrams(draft.grams)) }
    val step = if (unit == ServingUnit.GRAMS) 5.0 else 0.25
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun set(a: Double) {
        amount = amountShown(unit, a.coerceIn(0.0, unit.fromGrams(MAX_GRAMS)))
        draft.grams = gramsFor(unit, amount)
    }
    // Toggling the unit is a change of view, not of portion: it re-quantises the stored grams to the
    // new display's precision, which can only coarsen (0.1 g -> whole g) and never invents precision,
    // so g -> oz -> g returns the same number the user started with.
    fun toggleUnit() {
        unit = if (unit == ServingUnit.GRAMS) ServingUnit.OUNCES else ServingUnit.GRAMS
        draft.grams = gramsFor(unit, unit.fromGrams(draft.grams))
        amount = unit.fromGrams(draft.grams)
    }
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
                CompactChip(onClick = { toggleUnit() }, label = { Text(if (unit == ServingUnit.GRAMS) "oz" else "g") })
            }
            Spacer(Modifier.height(8.dp))
            Chip(onClick = onNext, label = { Text("Next") }, colors = ChipDefaults.primaryChipColors())
        }
    }
}
