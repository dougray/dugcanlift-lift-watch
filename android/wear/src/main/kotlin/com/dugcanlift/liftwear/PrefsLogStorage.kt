package com.dugcanlift.liftwear
import android.content.Context
import android.util.Base64
import com.dugcanlift.liftkit.LogStorage

/** SharedPreferences-backed bytes, the watch's equivalent of UserDefaults on watchOS. */
class PrefsLogStorage(context: Context) : LogStorage {
    private val prefs = context.applicationContext.getSharedPreferences("liftwear", Context.MODE_PRIVATE)
    override fun read(): ByteArray? = prefs.getString(KEY, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    override fun write(bytes: ByteArray) { prefs.edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply() }
    private companion object { const val KEY = "standalone_food_log" }
}
