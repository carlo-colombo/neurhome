package ovh.litapp.neurhome3.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiContextTest {
    @Test
    fun connectedWifiRetainsOnlyUsableSsid() {
        assertEquals(
            WifiContext(WifiContextState.CONNECTED, "Home network"),
            classifyWifiContext(true, WifiObservation.CONNECTED, "\"Home network\"")
        )
        assertEquals(
            WifiContext.UNKNOWN,
            classifyWifiContext(true, WifiObservation.CONNECTED, "<unknown ssid>")
        )
    }

    @Test
    fun knownDisconnectionIsDifferentFromUnknownContext() {
        val disconnected = classifyWifiContext(true, WifiObservation.DISCONNECTED)

        assertEquals(WifiContextState.NO_WIFI, disconnected.state)
        assertNull(disconnected.ssid)
        assertEquals(WifiContext.UNKNOWN, classifyWifiContext(false, WifiObservation.DISCONNECTED))
        assertEquals(WifiContext.UNKNOWN, classifyWifiContext(true, WifiObservation.NOT_INITIALIZED))
        assertEquals(WifiContext.UNKNOWN, classifyWifiContext(true, WifiObservation.UNAVAILABLE))
    }

}
