package com.dugcanlift.liftwear
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

/** Codes are encoded ONCE when the screen opens. Done clears only what was shown; Back leaves the log intact. */
@Composable fun ExportScreen(log: StandaloneFoodLog, onDone: () -> Unit) {
    val shown = remember { log.entries }
    val codes = remember(shown) { StandaloneExport.codes(shown) }
    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing logged yet.", color = DclColors.Muted) }
        return
    }
    val sizePx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val bitmaps = remember(codes) { codes.map { QrBitmap.render(it, sizePx) } }
    val pager = rememberPagerState { codes.size + 1 }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        if (page < codes.size) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Image(bitmaps[page].asImageBitmap(), contentDescription = "Code ${page + 1} of ${codes.size}", modifier = Modifier.fillMaxSize())
                Text("${page + 1} / ${codes.size}", color = Color.Black, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
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
