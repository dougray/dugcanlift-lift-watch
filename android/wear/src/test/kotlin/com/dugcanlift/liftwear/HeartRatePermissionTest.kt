package com.dugcanlift.liftwear

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wear OS 6 (API 36) moved heart rate from `BODY_SENSORS` to the granular
 * `android.permission.health.READ_HEART_RATE` for an app targeting 36 or higher, which this one is.
 * Asking for `BODY_SENSORS` there asks for a permission the manifest no longer declares on that
 * version, and Health Services would hand over no heart rate even if it were granted.
 */
class HeartRatePermissionTest {
    @Test fun `Wear OS 5_1 and older ask for BODY_SENSORS`() {
        listOf(30, 33, 34, 35).forEach { sdk ->
            assertEquals("API $sdk", Manifest.permission.BODY_SENSORS, HeartRateRecorder.heartRatePermission(sdk))
        }
    }

    @Test fun `Wear OS 6 and newer ask for READ_HEART_RATE`() {
        listOf(36, 37).forEach { sdk ->
            assertEquals("API $sdk", "android.permission.health.READ_HEART_RATE", HeartRateRecorder.heartRatePermission(sdk))
        }
    }

    @Test fun `the written-out name is the platform's own constant`() {
        // android.health.connect.HealthPermissions is API 34's, so the app spells it out; this keeps
        // the spelling honest against the SDK it compiles with.
        assertEquals(android.health.connect.HealthPermissions.READ_HEART_RATE, HeartRateRecorder.READ_HEART_RATE)
    }
}
