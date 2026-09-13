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
    @Test fun `a log over prefs storage keeps entries across instances`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val food = WatchFood("Oats", 379.0, 13.15, 6.52, 67.7, 10.1)
        StandaloneFoodLog(PrefsLogStorage(ctx)).append(LoggedFood(food, 50.0, FoodLogMeal.BREAKFAST, System.currentTimeMillis() / 1000))
        assertEquals(1, StandaloneFoodLog(PrefsLogStorage(ctx)).entries.size)
    }
}
