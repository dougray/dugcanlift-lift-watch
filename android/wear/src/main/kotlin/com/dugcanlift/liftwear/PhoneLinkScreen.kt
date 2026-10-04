package com.dugcanlift.liftwear

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*

/**
 * "Pair with phone", and afterwards the link's state in one line.
 *
 * The confirmation code is the point of the screen: the phone shows the same six digits, and a
 * lifter with two watches on the bench can see which one they are adopting. The code is never sent
 * — both ends compute it from the two handshake nonces — so a device showing the right number is
 * a device that took part in this handshake.
 *
 * The radio runs only while this screen is on, or while the app is open with a phone already
 * remembered. Leaving here stops it, and the watch is standalone again.
 */
@Composable
fun PhoneLinkScreen(link: PhoneLinkPeripheral, store: PhoneLinkStore, onBack: () -> Unit) {
    val status by link.status.collectAsState()
    var asked by remember { mutableStateOf(PhoneLinkPeripheral.runtimePermissions.isEmpty()) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        link.start()
    }

    // Advertise while this screen is resumed, and stop when it is not. A watch whose LIFT is
    // closed must behave exactly as it did before the link existed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, asked) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (asked) link.start()
                Lifecycle.Event.ON_PAUSE -> link.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); link.stop() }
    }

    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Phone") } }

            when (val current = status) {
                is PhoneLinkPeripheral.Status.Confirming -> {
                    item {
                        Text(
                            text = current.code,
                            style = MaterialTheme.typography.display1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item { Caption("Does ${current.peerName} show this number?") }
                    item {
                        Chip(
                            onClick = { link.confirm(true) },
                            label = { Text("Yes, pair") },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Chip(
                            onClick = { link.confirm(false) },
                            label = { Text("No") },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is PhoneLinkPeripheral.Status.Linked -> {
                    item { Caption("Linked to ${current.peerName}.") }
                    item { ForgetChip(link, onBack) }
                }

                is PhoneLinkPeripheral.Status.Connected ->
                    item { Caption("Connected to ${current.peerName ?: "your phone"}…") }

                is PhoneLinkPeripheral.Status.Advertising -> item {
                    Caption(
                        if (store.isPaired) "Waiting for ${store.pairedName ?: "your phone"}."
                        else "On your phone, open LIFT and tap \"Pair with my watch\"."
                    )
                }

                is PhoneLinkPeripheral.Status.Unavailable -> {
                    item { Caption(current.reason) }
                    if (!asked) item { PairChip { permissions.launch(PhoneLinkPeripheral.runtimePermissions) } }
                }

                is PhoneLinkPeripheral.Status.Failed -> {
                    item { Caption(current.reason) }
                    item { PairChip { link.start() } }
                }

                PhoneLinkPeripheral.Status.Idle -> {
                    item {
                        Caption(
                            if (store.isPaired) "Paired with ${store.pairedName ?: "your phone"}."
                            else "LIFT works on its own. Pairing lets your phone send the day's workout."
                        )
                    }
                    item {
                        PairChip(if (store.isPaired) "Reconnect" else "Pair with phone") {
                            if (PhoneLinkPeripheral.runtimePermissions.isNotEmpty() && !asked) {
                                permissions.launch(PhoneLinkPeripheral.runtimePermissions)
                            } else {
                                link.start()
                            }
                        }
                    }
                    if (store.isPaired) item { ForgetChip(link, onBack) }
                }
            }
        }
    }
}

@Composable private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = DclColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable private fun PairChip(label: String = "Try again", onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable private fun ForgetChip(link: PhoneLinkPeripheral, onBack: () -> Unit) {
    Chip(
        onClick = { link.forget(); onBack() },
        label = { Text("Forget phone") },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
