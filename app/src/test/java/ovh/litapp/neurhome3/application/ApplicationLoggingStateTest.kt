package ovh.litapp.neurhome3.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.models.WifiContextState

/**
 * The capture extensions in `LogSSID.kt` write their observations here and the launch-logging
 * path reads this snapshot, so these invariants keep a disabled or failed capture from being
 * mistaken for a captured context. Registering the network callback and the disable-time reset
 * observed through `ConnectivityManager` both need a `Context`, so they are the capture path's
 * responsibility, not this holder's.
 */
class ApplicationLoggingStateTest {
    @Test
    fun startsWithoutACapturedContextOrEnabledLogging() {
        val state = ApplicationLoggingState()

        assertEquals(WifiContext.UNKNOWN, state.wifiContext)
        assertFalse(state.wifiLoggingEnabled)
        assertFalse(state.positionLoggingEnabled)
        assertNull(state.networkCallback)
    }

    @Test
    fun theCapturedContextIsReplacedOnlyByAnExplicitUpdate() {
        val state = ApplicationLoggingState()

        state.setWifiLoggingEnabled(true)
        state.updateWifiContext(WifiContext(WifiContextState.CONNECTED, "Home network"))
        assertEquals(WifiContext(WifiContextState.CONNECTED, "Home network"), state.wifiContext)

        // Disabling the flag alone keeps the last snapshot: `disableSSIDLogging` pushes
        // WifiContext.UNKNOWN itself before it unregisters the callback.
        state.setWifiLoggingEnabled(false)
        assertEquals(WifiContext(WifiContextState.CONNECTED, "Home network"), state.wifiContext)

        state.updateWifiContext(WifiContext.UNKNOWN)
        assertEquals(WifiContext.UNKNOWN, state.wifiContext)
    }

    @Test
    fun loggingSwitchesAreTrackedIndependently() {
        val state = ApplicationLoggingState()

        state.setWifiLoggingEnabled(true)
        assertTrue(state.wifiLoggingEnabled)
        assertFalse(state.positionLoggingEnabled)

        state.setPositionLoggingEnabled(true)
        state.setWifiLoggingEnabled(false)
        assertFalse(state.wifiLoggingEnabled)
        assertTrue(state.positionLoggingEnabled)
    }
}
