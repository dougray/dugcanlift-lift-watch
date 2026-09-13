package com.dugcanlift.liftwear
import androidx.test.core.app.ApplicationProvider
import com.dugcanlift.liftkit.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsLogStorageTest {
    @Test fun `round trips bytes through SharedPreferences and survives a new instance`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PrefsLogStorage(ctx).write("hello".toByteArray())
        assertEquals("hello", String(PrefsLogStorage(ctx).read()!!))
    }
    @Test fun `a fresh install reads null`() {
        assertNull(PrefsLogStorage(ApplicationProvider.getApplicationContext()).read())
    }
    /** A6: the blob an append could not decode must survive the append, in its own key. */
    @Test fun `quarantined bytes land in their own key and the first one is kept`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val storage = PrefsLogStorage(ctx)
        storage.write("live".toByteArray())
        storage.quarantine("the undecodable original".toByteArray())
        storage.quarantine("a later failure".toByteArray())
        storage.write("replacement".toByteArray())
        assertEquals("replacement", String(PrefsLogStorage(ctx).read()!!))
        val prefs = ctx.getSharedPreferences("liftwear", android.content.Context.MODE_PRIVATE)
        val parked = android.util.Base64.decode(prefs.getString("standalone_food_log.corrupt", null)!!, android.util.Base64.NO_WRAP)
        assertEquals("the undecodable original", String(parked))
    }

    @Test fun `an append over an undecodable blob parks it instead of destroying it`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PrefsLogStorage(ctx).write("<<not json>>".toByteArray())
        val food = WatchFood("Oats", 379.0, 13.15, 6.52, 67.7, 10.1)
        StandaloneFoodLog(PrefsLogStorage(ctx)).append(LoggedFood(food, 50.0, FoodLogMeal.BREAKFAST, System.currentTimeMillis() / 1000))
        val prefs = ctx.getSharedPreferences("liftwear", android.content.Context.MODE_PRIVATE)
        val parked = android.util.Base64.decode(prefs.getString("standalone_food_log.corrupt", null)!!, android.util.Base64.NO_WRAP)
        assertEquals("<<not json>>", String(parked))
        assertEquals(1, StandaloneFoodLog(PrefsLogStorage(ctx)).entries.size)
    }

    @Test fun `a log over prefs storage keeps entries across instances`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val food = WatchFood("Oats", 379.0, 13.15, 6.52, 67.7, 10.1)
        StandaloneFoodLog(PrefsLogStorage(ctx)).append(LoggedFood(food, 50.0, FoodLogMeal.BREAKFAST, System.currentTimeMillis() / 1000))
        assertEquals(1, StandaloneFoodLog(PrefsLogStorage(ctx)).entries.size)
    }
}
