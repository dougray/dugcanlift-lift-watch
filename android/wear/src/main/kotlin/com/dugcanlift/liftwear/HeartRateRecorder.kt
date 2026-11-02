package com.dugcanlift.liftwear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.startExercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Heart rate, and a **real exercise session**, for a lifting session on the wrist.
 *
 * This is Wear's answer to what `LiftingSessionRecorder` does on the Apple Watch with
 * `HKWorkoutSession`: not only the number on screen, but the platform being told that the person is
 * exercising, which is what buys sensor access and runtime while the wrist is down between sets.
 *
 * **Health Services is not Play Services.** `androidx.health:health-services-client` is an AndroidX
 * artifact; the service it binds to is `com.google.android.wearable.healthservices`, a system package
 * present on the watch. Its whole dependency tree was resolved and read before this file was written
 * and contains no `com.google.android.gms` at any depth — the only `com.google.*` coordinate under it
 * is Guava, whose `ListenableFuture` is the `*Async` methods' return type. **This file uses the
 * library's own `suspend` extensions instead**, which return `Unit`, so Guava is not on the compile
 * classpath and never reaches the APK's own code. `PhoneLinkManifestTest` still guards the manifest,
 * and there is still no `INTERNET` permission.
 *
 * **Which permission depends on the watch.** Wear OS 6 (API 36) moved heart rate from `BODY_SENSORS`
 * to the granular `android.permission.health.READ_HEART_RATE` for any app targeting API 36 or higher,
 * and this one targets 37: on a Wear OS 6 watch `BODY_SENSORS` is not even declared (the manifest
 * caps it at 35) and would grant Health Services nothing. [heartRatePermission] picks the one the
 * running watch uses, and both the check and the request go through it.
 *
 * **Nothing here is load-bearing.** A refused heart-rate permission, a watch whose Health Services is too
 * old, an exercise type it will not start: each costs the heart rate and the background runtime, never
 * the workout. Sets go into the `WorkoutDraft` either way.
 *
 * **No number is shown until a sample has actually landed.** A zero, or a dash pretending to be a
 * reading, is worse than a line that is not there — the Apple watch's `HeartRateLabel` rule, and a
 * watch sitting on a table produces no samples at all.
 */
class HeartRateRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val client by lazy { HealthServices.getClient(appContext).exerciseClient }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private var samples = 0
    private var total = 0.0
    private var highest = 0.0

    @Volatile private var running = false

    /**
     * Average and maximum over the session, or `null` when nothing was ever measured — which is what
     * a simulator, a refused permission and a watch on a bench all produce, and none of them is a
     * heart rate of zero.
     */
    val summary: Pair<Int, Int>?
        get() = if (samples == 0 || highest <= 0.0) null else (total / samples).toInt() to highest.toInt()

    val hasPermission: Boolean
        get() = appContext.checkSelfPermission(heartRatePermission()) == PackageManager.PERMISSION_GRANTED

    private val callback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            running = false
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latest = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value ?: return
            if (latest <= 0.0) return
            samples++
            total += latest
            if (latest > highest) highest = latest
            _bpm.value = Math.round(latest).toInt()
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            // A sensor that has stopped measuring clears the line rather than freezing the last
            // number on screen: a stale bpm looks exactly like a live one.
            if (dataType == DataType.HEART_RATE_BPM &&
                availability is DataTypeAvailability &&
                availability != DataTypeAvailability.AVAILABLE
            ) {
                _bpm.value = null
            }
        }
    }

    /**
     * Starts a weightlifting exercise. Safe to call when Health Services is absent or the permission
     * was refused: it returns having done nothing, and the lifting session is unaffected.
     */
    fun start() {
        if (running || !hasPermission) return
        running = true
        samples = 0
        total = 0.0
        highest = 0.0
        _bpm.value = null
        val config = ExerciseConfig.Builder(ExerciseType.WEIGHTLIFTING)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .build()
        scope.launch {
            runCatching {
                client.setUpdateCallback(callback)
                client.startExercise(config)
            }.onFailure { running = false }
        }
    }

    /** Ends the exercise. [summary]'s two numbers survive until the next [start]. */
    fun stop() {
        if (!running) return
        running = false
        _bpm.value = null
        scope.launch {
            runCatching { client.endExercise() }
            runCatching { client.clearUpdateCallback(callback) }
        }
    }

    companion object {
        /**
         * `HealthPermissions.READ_HEART_RATE`, written out: the constant is API 34's, and this app
         * runs from API 30. Only ever asked for on API 36 and up.
         */
        const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

        /**
         * What Health Services needs to hand over a heart rate on a watch running [sdk]:
         * `READ_HEART_RATE` from Wear OS 6 (API 36, Android 16), `BODY_SENSORS` before it. The
         * manifest declares each for exactly those versions, so the app never asks for one the
         * watch would refuse to grant.
         */
        fun heartRatePermission(sdk: Int = Build.VERSION.SDK_INT): String =
            if (sdk >= Build.VERSION_CODES.BAKLAVA) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

        /** The heart-rate permission for this watch, as the runtime request takes it. */
        val runtimePermissions: Array<String>
            get() = arrayOf(heartRatePermission())
    }
}
