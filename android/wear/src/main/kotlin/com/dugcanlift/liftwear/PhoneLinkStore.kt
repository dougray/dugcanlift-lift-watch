package com.dugcanlift.liftwear

import android.content.Context

/**
 * The one phone this watch is paired with, remembered across launches.
 *
 * SharedPreferences, for the reason [PrefsLogStorage] uses it: it is an address, a name and a
 * timestamp, and it needs no dependency. A Bluetooth address is not a training log — but it is
 * still the user's, so it is written with `allowBackup="false"` like everything else here and
 * leaves the watch only over the link it names.
 *
 * **One phone at a time.** A second pairing replaces the first rather than joining it. A lifter
 * has one phone; a list would be a list with one thing in it and a screen to manage it.
 */
class PhoneLinkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("liftwear_link", Context.MODE_PRIVATE)

    val pairedAddress: String? get() = prefs.getString(KEY_ADDRESS, null)
    val pairedName: String? get() = prefs.getString(KEY_NAME, null)
    val pairedAtEpochSeconds: Long get() = prefs.getLong(KEY_AT, 0L)

    val isPaired: Boolean get() = pairedAddress != null

    /** Addresses compare case-insensitively: Android hands them back upper case, and a stored
     *  lower-case one from a different API level must still match the same phone. */
    fun isPaired(address: String?): Boolean =
        address != null && pairedAddress?.equals(address, ignoreCase = true) == true

    fun remember(address: String, name: String, atEpochSeconds: Long = System.currentTimeMillis() / 1000) {
        prefs.edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .putLong(KEY_AT, atEpochSeconds)
            .apply()
    }

    /** "Forget phone". The watch goes back to exactly what it was: standalone, exporting by QR.
     *  The Bluetooth bond itself is the system's and is removed in the watch's own settings — this
     *  only stops LIFT from talking to it, which is what the button on this screen promises. */
    fun forget() {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).remove(KEY_AT).apply()
    }

    private companion object {
        const val KEY_ADDRESS = "paired_phone_address"
        const val KEY_NAME = "paired_phone_name"
        const val KEY_AT = "paired_at"
    }
}
