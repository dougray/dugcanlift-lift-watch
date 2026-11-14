package com.dugcanlift.liftwear

import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseType
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What joins [RunHeartRateMode] to the radio and to the lifting session, on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class RunHeartRateWiringTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `a run is Health Services' RUNNING with no GPS and heart rate alone`() {
        val config = HealthServicesExerciseClient.runConfig()
        assertEquals(ExerciseType.RUNNING, config.exerciseType)
        assertFalse(config.isGpsEnabled)
        assertEquals(setOf(DataType.HEART_RATE_BPM), config.dataTypes)
    }

    @Test fun `sending a reading with no link, or a bad one, is dropped without a crash`() {
        val link = PhoneLinkPeripheral(context, PhoneLinkStore(context))
        link.sendRunHeartRate(142, 1_762_800_000L)     // no phone
        link.sendRunHeartRate(0, 1_762_800_000L)       // not a heart rate
        link.sendRunHeartRate(-4, -1L)
        link.stop()                                    // and the radio going off with no run mode is fine
    }

    @Test fun `the radio going off ends a run in progress`() {
        val ended = java.util.concurrent.atomic.AtomicInteger()
        val link = PhoneLinkPeripheral(context, PhoneLinkStore(context))
        link.runMode = RunHeartRateMode(
            exercise = object : ExerciseClientLike {
                override val hasPermission = true
                override suspend fun trackedStatus() =
                    if (running) ExerciseClientLike.Tracked.OWNED_RUN else ExerciseClientLike.Tracked.NONE
                private var running = false
                override suspend fun startRun(
                    onReadings: (List<com.dugcanlift.liftkit.HeartRatePerSet.Sample>) -> Unit,
                    onEnded: () -> Unit,
                    onRegistrationFailed: () -> Unit,
                ) { running = true }
                override suspend fun adoptRun(
                    onReadings: (List<com.dugcanlift.liftkit.HeartRatePerSet.Sample>) -> Unit,
                    onEnded: () -> Unit,
                    onRegistrationFailed: () -> Unit,
                ) = Unit
                override suspend fun end() { running = false; ended.incrementAndGet() }
            },
            send = { _, _ -> },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        link.runMode!!.start()
        assertTrue(link.runMode!!.isRunning)
        link.stop()
        assertFalse(link.runMode!!.isRunning)
        assertEquals(1, ended.get())
    }
}
