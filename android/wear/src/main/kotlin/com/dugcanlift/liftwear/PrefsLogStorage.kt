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
     * user's original data, and a later failed decode of a *fresh* log is worth less — but a second
     * corruption still counts against [DISCARDED_KEY] rather than vanishing with no trace.
     */
    override fun quarantine(bytes: ByteArray) {
        if (prefs.contains(CORRUPT_KEY)) {
            prefs.edit().putInt(DISCARDED_KEY, prefs.getInt(DISCARDED_KEY, 0) + 1).commit()
            return
        }
        prefs.edit().putString(CORRUPT_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
    }

    override fun quarantineDiscardedCount(): Int = prefs.getInt(DISCARDED_KEY, 0)

    private companion object {
        const val KEY = "standalone_food_log"
        const val CORRUPT_KEY = "standalone_food_log.corrupt"
        const val DISCARDED_KEY = "standalone_food_log.corrupt.discarded"
    }
}
