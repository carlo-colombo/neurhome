package ovh.litapp.neurhome3.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPositionTest {
    @Test
    fun locationCaptureRequiresSettingAndPermission() {
        assertTrue(shouldCaptureLocation(loggingEnabled = true, hasPermission = true))
        assertFalse(shouldCaptureLocation(loggingEnabled = false, hasPermission = true))
        assertFalse(shouldCaptureLocation(loggingEnabled = true, hasPermission = false))
    }

    @Test
    fun currentLocationMustBeRecentAndUsable() {
        assertTrue(isUsableLocationFix(37.0, -122.0, 15.0, 60_000))
        assertFalse(isUsableLocationFix(37.0, -122.0, 15.0, 15 * 60 * 1000L + 1))
        assertFalse(isUsableLocationFix(91.0, -122.0, 15.0, 1_000))
        assertFalse(isUsableLocationFix(37.0, -122.0, 0.0, 1_000))
        assertFalse(isUsableLocationFix(37.0, -122.0, 5_001.0, 1_000))
        assertFalse(isUsableLocationFix(37.0, -122.0, 15.0, -1))
    }
}
