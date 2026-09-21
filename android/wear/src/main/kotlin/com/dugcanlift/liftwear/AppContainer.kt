package com.dugcanlift.liftwear
import android.content.Context
import com.dugcanlift.liftkit.*
import kotlinx.coroutines.*

/** One instance per process. The library parse is 634 KB of JSON, so it is started once, off the main thread. */
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val library: Deferred<WatchFoodLibrary> = scope.async { WatchFoodLibrary.bundled() }
    val log: StandaloneFoodLog = StandaloneFoodLog(PrefsLogStorage(context))

    /** The phone link. Constructing it touches no radio — [PhoneLinkPeripheral.start] does, and
     *  only the Phone screen calls that, so a watch that never pairs pays nothing for this. */
    val linkStore: PhoneLinkStore = PhoneLinkStore(context)
    val phoneLink: PhoneLinkPeripheral = PhoneLinkPeripheral(context, linkStore)
    companion object {
        @Volatile private var instance: AppContainer? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AppContainer(context.applicationContext).also { instance = it } }
    }
}
