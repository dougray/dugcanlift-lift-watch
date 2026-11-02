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
     *  only the Phone screen and a session call that, so a watch that never pairs pays nothing. */
    val linkStore: PhoneLinkStore = PhoneLinkStore(context)
    val phoneLink: PhoneLinkPeripheral = PhoneLinkPeripheral(context, linkStore)

    /** The plan, the session in progress, and what the phone has not acknowledged. */
    val sessionStore: SessionStore = SessionStore(context)

    /** Heart rate and a real exercise session. Constructing it binds nothing — [HeartRateRecorder.start]
     *  does — so a watch that never lifts pays nothing for it either. */
    val heartRate: HeartRateRecorder = HeartRateRecorder(context)

    /** One per process, like everything else here: it holds the workout being trained, and a second
     *  copy would be a second opinion about where the lifter is. */
    val session: SessionController = SessionController(sessionStore, phoneLink, heartRate)
    companion object {
        @Volatile private var instance: AppContainer? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AppContainer(context.applicationContext).also { instance = it } }
    }
}
