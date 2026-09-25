package com.dugcanlift.liftwear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The buzz at the end of a rest interval.
 *
 * The point of a rest timer on a wrist is not having to look at it, so this is the feature rather than
 * a flourish. `VIBRATE` is an install-time permission and asks the user nothing; nothing here touches
 * the network or Play Services.
 *
 * Two short pulses rather than one long one: a single buzz on a watch is what a notification from
 * anything else feels like, and "your rest is over" should be distinguishable without looking.
 */
fun buzzRestOver(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    } ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
}
