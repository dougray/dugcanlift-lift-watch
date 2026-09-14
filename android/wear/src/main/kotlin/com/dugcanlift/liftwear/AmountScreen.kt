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

/** Matches watchOS's library-flow floor (FoodSearchView.swift's `Stepper(value: $grams, in: 5...1000...)`).
 *  Below this a 0 g / 0 kcal entry was loggable -- meaningless, but it still spent a slot in the 200-entry
 *  cap and a row in an exported QR code. Derived the same way as [MAX_GRAMS]'s ounce side: the floor is
 *  enforced in grams, and the display unit's clamp is converted from it rather than given its own literal. */
private const val MIN_GRAMS = 5.0

/**
 * The precision the screen shows, which is also the precision that gets logged. Grams display as
 * whole numbers, so grams are stored whole; ounces display to 0.1 oz (watchOS's one decimal place,
 * adopted 2026-09-13 along with its 0.5 oz step), and 0.1 oz is 2.8 g, so 0.1 g still records the
 * shown value with room to spare. Storing the raw conversion instead put 92.135875 g in the record
 * and on the wire behind a screen reading "92 g" — and `num()` only shortens whole doubles, so
 * every such entry cost ~9 characters of an 800-byte code rather than 2. watchOS does store the
 * raw conversion; that is the one place here we deliberately do not follow it, because the QR code
 * is Wear's only way off the watch and watchOS has a phone to sync to.
 */
internal fun amountShown(unit: ServingUnit, amount: Double): Double =
    if (unit == ServingUnit.GRAMS) round(amount) else round(amount * 10.0) / 10.0

/**
 * What the big number reads: whole numbers bare, ounces otherwise to one decimal. Transcribed from
 * watchOS's `FoodAmountEntryView.formattedAmount`, which takes it in turn from the phone app's
 * `FoodEntryDisplay`. With a 0.5 oz step every reachable value is already .0 or .5, so the second
 * decimal place this used to print was never anything but a zero.
 */
internal fun amountLabel(unit: ServingUnit, amount: Double): String =
    if (unit == ServingUnit.GRAMS) amount.roundToInt().toString()
    else if (amount == round(amount)) amount.roundToInt().toString()
    else "%.1f".format(amount)

/** The grams to record for an amount the user set in [unit]. Never finer than [amountShown]. */
internal fun gramsFor(unit: ServingUnit, amount: Double): Double =
    if (unit == ServingUnit.GRAMS) round(amount) else round(unit.toGrams(amount) * 10.0) / 10.0

/**
 * What `set()` computes: the amount to show and the grams to store for a requested value in [unit].
 * The initial clamp to `unit.fromGrams(MAX_GRAMS)`/`unit.fromGrams(MIN_GRAMS)` runs in the display
 * unit's own precision, and 0.01-oz snapping can round a clamped bound past it in either direction
 * (2000 g -> 70.5477 oz, which 0.01-oz snapping rounds up to 70.55 oz -> 2000.1 g; the 5 g floor is
 * 0.1763 oz, which snaps to 0.18 oz -> 5.1 g) -- so the grams are re-clamped after snapping, never
 * before, and on both bounds.
 */
internal fun clampedAmount(unit: ServingUnit, requested: Double): Pair<Double, Double> {
    val shown = amountShown(unit, requested.coerceIn(unit.fromGrams(MIN_GRAMS), unit.fromGrams(MAX_GRAMS)))
    return shown to gramsFor(unit, shown).coerceIn(MIN_GRAMS, MAX_GRAMS)
}

/** Grams (or ounces) via the rotary input and +/-; kcal for the chosen amount updates live. */
@Composable fun AmountScreen(draft: Draft, onNext: () -> Unit) {
    val food = draft.food ?: return
    // Not screen-local: the chosen unit lives on the draft so it survives logging a food, the way
    // watchOS's `session.servingUnit` does.
    val unit = draft.unit
    var amount by remember(unit) { mutableStateOf(unit.fromGrams(draft.grams)) }
    val step = if (unit == ServingUnit.GRAMS) 5.0 else 0.5
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun set(a: Double) {
        val (shown, grams) = clampedAmount(unit, a)
        amount = shown
        draft.grams = grams
    }
    // Toggling the unit is a change of view, not of portion: it re-quantises the stored grams to the
    // new display's precision, which can only coarsen (0.1 g -> whole g) and never invents precision,
    // so g -> oz -> g returns the same number the user started with.
    fun toggleUnit() {
        val next = if (unit == ServingUnit.GRAMS) ServingUnit.OUNCES else ServingUnit.GRAMS
        draft.unit = next
        draft.grams = gramsFor(next, next.fromGrams(draft.grams))
        amount = next.fromGrams(draft.grams)
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
            Text("${amountLabel(unit, amount)} ${if (unit == ServingUnit.GRAMS) "g" else "oz"}", style = MaterialTheme.typography.display2)
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
