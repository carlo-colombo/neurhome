package ovh.litapp.neurhome3.data.models

import androidx.room.TypeConverter

enum class WifiContextState {
    CONNECTED,
    NO_WIFI,
    UNKNOWN
}

data class WifiContext(
    val state: WifiContextState,
    val ssid: String? = null
) {
    init {
        require(
            if (state == WifiContextState.CONNECTED) !ssid.isNullOrBlank() else ssid == null
        ) {
            "Only a connected Wi-Fi context may retain a non-empty SSID"
        }
    }

    companion object {
        val UNKNOWN = WifiContext(WifiContextState.UNKNOWN)
    }
}

enum class WifiObservation {
    CONNECTED,
    DISCONNECTED,
    NOT_INITIALIZED,
    UNAVAILABLE
}

/** Classifies only observed states; missing SSID/context is never treated as disconnection. */
fun classifyWifiContext(
    loggingEnabled: Boolean,
    observation: WifiObservation,
    ssid: String? = null
): WifiContext {
    if (!loggingEnabled) return WifiContext.UNKNOWN

    return when (observation) {
        WifiObservation.DISCONNECTED -> WifiContext(WifiContextState.NO_WIFI)
        WifiObservation.CONNECTED -> {
            val usableSsid = ssid?.trim()?.removeSurrounding("\"")?.takeIf {
                it.isNotEmpty() &&
                    !it.equals("<unknown ssid>", ignoreCase = true) &&
                    !it.equals("unknown ssid", ignoreCase = true)
            }
            if (usableSsid == null) WifiContext.UNKNOWN
            else WifiContext(WifiContextState.CONNECTED, usableSsid)
        }
        WifiObservation.NOT_INITIALIZED,
        WifiObservation.UNAVAILABLE -> WifiContext.UNKNOWN
    }
}

/**
 * Classifies the SSIDs observed for the Wi-Fi transports currently present. An empty list means
 * no Wi-Fi transport was found, which is a known disconnection; a present transport whose SSID
 * is missing or unusable stays [WifiContextState.UNKNOWN] rather than being treated as one.
 */
fun classifyWifiNetworks(wifiSsids: List<String?>): WifiContext =
    if (wifiSsids.isEmpty()) {
        classifyWifiContext(loggingEnabled = true, observation = WifiObservation.DISCONNECTED)
    } else {
        classifyWifiContext(
            loggingEnabled = true,
            observation = WifiObservation.CONNECTED,
            ssid = wifiSsids.firstOrNull { ssid ->
                !ssid.isNullOrBlank() && !ssid.equals("<unknown ssid>", ignoreCase = true)
            }
        )
    }

class WifiContextStateConverter {
    @TypeConverter
    fun fromState(state: WifiContextState): String = state.name

    @TypeConverter
    fun toState(value: String): WifiContextState =
        runCatching { WifiContextState.valueOf(value) }.getOrDefault(WifiContextState.UNKNOWN)
}
