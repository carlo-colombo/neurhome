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

    @Test
    fun connectedWithoutAUsableSsidIsUnknownNotDisconnected() {
        for (ssid in listOf(null, "", "   ", "unknown ssid", "UNKNOWN SSID", "<unknown SSID>")) {
            assertEquals(
                "ssid=$ssid",
                WifiContext.UNKNOWN,
                classifyWifiContext(true, WifiObservation.CONNECTED, ssid)
            )
        }
    }

    @Test
    fun connectedSsidIsTrimmedAndUnquoted() {
        assertEquals(
            WifiContext(WifiContextState.CONNECTED, "Home network"),
            classifyWifiContext(true, WifiObservation.CONNECTED, "  \"Home network\"  ")
        )
    }

    @Test
    fun capturingWithoutLoggingEnabledAlwaysYieldsUnknown() {
        assertEquals(
            WifiContext.UNKNOWN,
            classifyWifiContext(false, WifiObservation.CONNECTED, "Home network")
        )
        assertEquals(
            WifiContext.UNKNOWN,
            classifyWifiContext(false, WifiObservation.DISCONNECTED)
        )
    }

    @Test
    fun noObservedWifiTransportIsAKnownDisconnection() {
        val context = classifyWifiNetworks(emptyList())

        assertEquals(WifiContextState.NO_WIFI, context.state)
        assertNull(context.ssid)
    }

    @Test
    fun presentWifiTransportWithoutAUsableSsidIsUnknown() {
        assertEquals(WifiContext.UNKNOWN, classifyWifiNetworks(listOf(null)))
        assertEquals(WifiContext.UNKNOWN, classifyWifiNetworks(listOf("   ")))
        assertEquals(WifiContext.UNKNOWN, classifyWifiNetworks(listOf("<unknown ssid>")))
    }

    @Test
    fun firstUsableObservedSsidWins() {
        assertEquals(
            WifiContext(WifiContextState.CONNECTED, "Home network"),
            classifyWifiNetworks(listOf(null, "\"Home network\"", "Other network"))
        )
        assertEquals(
            WifiContext(WifiContextState.CONNECTED, "Home network"),
            classifyWifiNetworks(listOf("<unknown ssid>", "\"Home network\""))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun connectedContextRequiresAnSsid() {
        WifiContext(WifiContextState.CONNECTED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonConnectedContextRejectsAnSsid() {
        WifiContext(WifiContextState.NO_WIFI, "Home network")
    }
}
