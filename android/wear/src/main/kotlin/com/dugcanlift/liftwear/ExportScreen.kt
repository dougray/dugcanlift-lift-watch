package com.dugcanlift.liftwear
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.*
import androidx.wear.compose.material.dialog.Dialog
import com.dugcanlift.liftkit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Matches ExportFoodsView.swift's confirmationDialog verbatim: title, message and button labels. */
internal const val CLEAR_LOG_TITLE = "Clear the log?"
internal const val CLEAR_LOG_MESSAGE = "Only do this once the codes have been scanned. This cannot be undone."

/** Since 2026-09-13 an empty export really does mean an empty log: age neither deletes nor hides,
 *  so there is no longer an "entries exist but all aged out" state to distinguish. */
internal fun exportEmptyMessage(): String = "Nothing logged yet."

/** watchOS suppresses the "n / total" caption entirely for a single-code export (ExportFoodsView.swift
 *  `if codes.count > 1`), both because "1 / 1" tells the user nothing and because the caption's band
 *  eats module area on the one screen where module size decides whether a phone can focus. */
internal fun exportPageCaption(page: Int, total: Int): String? = if (total > 1) "${page + 1} / $total" else null

/** watchOS: "Scan all N codes, then:" / "Scanned it?" (ExportFoodsView.swift:73-75). */
internal fun exportConfirmPrompt(total: Int): String = if (total > 1) "Scanned all $total?" else "Scanned it?"

/** No caption is drawn for a single code, so reserve no room for it -- the code gets the whole square. */
internal fun captionReserveFor(total: Int, captionReservePx: Int): Int = if (total > 1) captionReservePx else 0

/** Codes are encoded ONCE when the screen opens. Done clears only what was shown; Back leaves the log intact. */
@Composable fun ExportScreen(log: StandaloneFoodLog, onDone: () -> Unit) {
    val shown = remember { log.entries }
    val codes = remember(shown) { StandaloneExport.codes(shown) }
    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(exportEmptyMessage(), color = DclColors.Muted)
        }
        return
    }
    val density = LocalDensity.current
    val isRound = LocalConfiguration.current.isScreenRound
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    // The band the "n / total" caption occupies: its line height (body1, 20 sp — sp.toPx() folds in
    // the user's font scale) plus its 6 dp offset from the edge and a little clearance. Measured
    // rather than assumed because on a square display the code would otherwise be drawn under it.
    val captionReservePx = with(density) { 20.sp.roundToPx() + 10.dp.roundToPx() }
    val sizePx = remember(screenWidthPx, isRound, captionReservePx, codes.size) {
        QrBitmap.qrSizePx(screenWidthPx, isRound, captionReserveFor(codes.size, captionReservePx))
    }
    val sizeDp = with(density) { sizePx.toDp() }
    // Rendered pages, kept across a swipe away and back: produceState restarting from null blanked
    // the code to white while a phone camera was aimed at it. Bounded to the current page and its
    // neighbours, which is all the pager can show.
    val rendered = remember(codes, sizePx) { mutableStateMapOf<Int, Bitmap>() }
    val pager = rememberPagerState { codes.size + 1 }
    // The Wear pager, not the generic one: inside a SwipeDismissableNavHost the generic pager eats
    // the swipe-right-from-the-left-edge back gesture, leaving paging forward through every code as
    // the only way home. PagerDefaults.gestureInclusion (the default here) reserves that left edge
    // zone for swipe-to-dismiss and keeps the rest of the horizontal drag for the pager.
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page < codes.size) {
            // Full-bleed white background is deliberate: it maximises quiet-zone contrast on AMOLED.
            // The code itself is inscribed in the display's circle so its finder-pattern corners survive.
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                LaunchedEffect(page, codes, sizePx) {
                    if (rendered[page] == null) {
                        val bitmap = withContext(Dispatchers.Default) { QrBitmap.render(codes[page], sizePx) }
                        rendered.keys.filter { abs(it - page) > 1 }.forEach { rendered.remove(it) }
                        rendered[page] = bitmap
                    }
                }
                val current = rendered[page]
                if (current != null) {
                    // FilterQuality.None: a barcode must never be interpolated. The bitmap is drawn
                    // 1:1 here, but density rounding can still leave a sub-pixel scale.
                    Image(current.asImageBitmap(), contentDescription = "Code ${page + 1} of ${codes.size}",
                          modifier = Modifier.size(sizeDp), filterQuality = FilterQuality.None)
                } else {
                    Spacer(Modifier.size(sizeDp))
                }
                exportPageCaption(page, codes.size)?.let {
                    Text(it, color = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                }
            }
        } else {
            // Matches watchOS: the destructive tap only arms a confirmation, it never clears directly.
            // Overshooting the pager onto this page and hitting the primary chip used to wipe the only
            // copy of the log with no undo (ExportFoodsView.swift:78 gates the same way).
            var confirmingClear by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(exportConfirmPrompt(codes.size), color = DclColors.Text)
                Spacer(Modifier.height(8.dp))
                Chip(onClick = { confirmingClear = true }, label = { Text("Done — clear these") }, colors = ChipDefaults.primaryChipColors())
                Spacer(Modifier.height(4.dp))
                Chip(onClick = onDone, label = { Text("Keep them") }, colors = ChipDefaults.secondaryChipColors())
            }
            Dialog(showDialog = confirmingClear, onDismissRequest = { confirmingClear = false }) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(CLEAR_LOG_TITLE, style = MaterialTheme.typography.title3, textAlign = TextAlign.Center, color = DclColors.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(CLEAR_LOG_MESSAGE, textAlign = TextAlign.Center, color = DclColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    Chip(onClick = { confirmingClear = false; log.remove(shown); onDone() }, label = { Text("Clear") }, colors = ChipDefaults.primaryChipColors())
                    Spacer(Modifier.height(4.dp))
                    Chip(onClick = { confirmingClear = false }, label = { Text("Keep") }, colors = ChipDefaults.secondaryChipColors())
                }
            }
        }
    }
}
