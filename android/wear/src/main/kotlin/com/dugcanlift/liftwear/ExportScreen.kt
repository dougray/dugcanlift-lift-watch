package com.dugcanlift.liftwear
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.dugcanlift.liftkit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Codes are encoded ONCE when the screen opens. Done clears only what was shown; Back leaves the log intact. */
@Composable fun ExportScreen(log: StandaloneFoodLog, onDone: () -> Unit) {
    val shown = remember { log.entries }
    val codes = remember(shown) { StandaloneExport.codes(shown) }
    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing logged yet.", color = DclColors.Muted) }
        return
    }
    val density = LocalDensity.current
    val isRound = LocalConfiguration.current.isScreenRound
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val sizePx = remember(screenWidthPx, isRound) { QrBitmap.qrSizePx(screenWidthPx, isRound) }
    val sizeDp = with(density) { sizePx.toDp() }
    val pager = rememberPagerState { codes.size + 1 }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page < codes.size) {
            // Full-bleed white background is deliberate: it maximises quiet-zone contrast on AMOLED.
            // The code itself is inscribed in the display's circle so its finder-pattern corners survive.
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                val bitmap by produceState<Bitmap?>(null, page, codes, sizePx) {
                    value = withContext(Dispatchers.Default) { QrBitmap.render(codes[page], sizePx) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val current = bitmap
                    if (current != null) {
                        Image(current.asImageBitmap(), contentDescription = "Code ${page + 1} of ${codes.size}", modifier = Modifier.size(sizeDp))
                    } else {
                        Spacer(Modifier.size(sizeDp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${page + 1} / ${codes.size}", color = Color.Black)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Scanned all ${codes.size}?", color = DclColors.Text)
                Spacer(Modifier.height(8.dp))
                Chip(onClick = { log.remove(shown); onDone() }, label = { Text("Done — clear these") }, colors = ChipDefaults.primaryChipColors())
                Spacer(Modifier.height(4.dp))
                Chip(onClick = onDone, label = { Text("Keep them") }, colors = ChipDefaults.secondaryChipColors())
            }
        }
    }
}
