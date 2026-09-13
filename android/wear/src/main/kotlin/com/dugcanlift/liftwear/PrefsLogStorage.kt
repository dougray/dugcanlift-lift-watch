package com.dugcanlift.liftwear
import android.content.Context
import android.util.Base64
import com.dugcanlift.liftkit.LogStorage

/** SharedPreferences-backed bytes, the watch's equivalent of UserDefaults on watchOS. */
class PrefsLogStorage(context: Context) : LogStorage {
    private val prefs = context.applicationContext.getSharedPreferences("liftwear", Context.MODE_PRIVATE)
    override fun read(): ByteArray? = prefs.getString(KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    override fun write(bytes: ByteArray) { prefs.edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply() }

    /**
     * Parks a blob the log could not decode in its own key so the overwrite that follows cannot
     * destroy it. `commit()`, not `apply()`: the caller writes the replacement immediately after,
     * and this must be on disk first. The first quarantine wins — it is the copy closest to the
     * user's original data, and a later failed decode of a *fresh* log is worth less.
     */
    override fun quarantine(bytes: ByteArray) {
        if (prefs.contains(CORRUPT_KEY)) return
        prefs.edit().putString(CORRUPT_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
    }

    private companion object {
        const val KEY = "standalone_food_log"
        const val CORRUPT_KEY = "standalone_food_log.corrupt"
    }
}
