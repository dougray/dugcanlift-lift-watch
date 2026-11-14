package com.dugcanlift.liftwear

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.startExercise
import com.dugcanlift.liftkit.HeartRatePerSet
import java.time.Instant

/**
 * The real [ExerciseClientLike]: Health Services' exercise client, as [HeartRateRecorder] uses it for
 * lifting. Not Play Services (see `HeartRateRecorder`'s comment), and it uses the library's own
 * `suspend` extensions for the same reason.
 *
 * **Not exercised by a unit test:** it is a thin wrapper over a system service that does not exist on
 * the JVM. The rules around it are [RunHeartRateMode]'s and are tested there; this is proven on a
 * watch or not at all.
 */
class HealthServicesExerciseClient(context: Context) : ExerciseClientLike {
    private val appContext = context.applicationContext
    private val client by lazy { HealthServices.getClient(appContext).exerciseClient }
    private var callback: ExerciseUpdateCallback? = null

    override val hasPermission: Boolean
        get() = appContext.checkSelfPermission(HeartRateRecorder.heartRatePermission()) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * `RestrictedApi` is suppressed for [ExerciseTrackedStatus] alone, as in [HeartRateRecorder.resume]:
     * the public `exerciseTrackedStatus` returns exactly those values and there is no other way to read
     * them.
     */
    @SuppressLint("RestrictedApi")
    override suspend fun trackedStatus(): ExerciseClientLike.Tracked =
        when (client.getCurrentExerciseInfo().exerciseTrackedStatus) {
            ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS -> ExerciseClientLike.Tracked.NONE
            ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> ExerciseClientLike.Tracked.OWNED_IN_PROGRESS
            // Anything else, including a status a later library adds, is "not ours to start over".
            else -> ExerciseClientLike.Tracked.OTHER_APP_IN_PROGRESS
        }

    override suspend fun startRun(onReadings: (List<HeartRatePerSet.Sample>) -> Unit, onEnded: () -> Unit) {
        val updates = object : ExerciseUpdateCallback {
            override fun onRegistered() = Unit
            override fun onRegistrationFailed(throwable: Throwable) = onEnded()
            override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
            override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) = Unit

            override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
                if (update.exerciseStateInfo.state.isEnded) {
                    onEnded()
                    return
                }
                val points = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                if (points.isEmpty()) return
                // A data point is timed from boot; the readings are wall-clock seconds.
                val boot = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
                onReadings(points.map { HeartRatePerSet.Sample(it.getTimeInstant(boot).toEpochMilli() / 1000.0, it.value) })
            }
        }
        callback = updates
        try {
            client.setUpdateCallback(updates)
            client.startExercise(runConfig())
        } catch (e: Throwable) {
            runCatching { client.clearUpdateCallback(updates) }
            callback = null
            throw e
        }
    }

    override suspend fun end() {
        val registered = callback
        callback = null
        runCatching { client.endExercise() }
        if (registered != null) runCatching { client.clearUpdateCallback(registered) }
    }

    companion object {
        /** A run, measured at the wrist: no GPS (the phone has the route), heart rate and nothing else. */
        fun runConfig(): ExerciseConfig = ExerciseConfig.Builder(ExerciseType.RUNNING)
            .setDataTypes(setOf(DataType.HEART_RATE_BPM))
            .setIsAutoPauseAndResumeEnabled(false)
            .setIsGpsEnabled(false)
            .build()
    }
}
