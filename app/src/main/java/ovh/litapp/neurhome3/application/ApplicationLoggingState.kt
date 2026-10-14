package ovh.litapp.neurhome3.application

import android.net.ConnectivityManager.NetworkCallback
import ovh.litapp.neurhome3.data.models.WifiContext

/**
 * Process-wide state backing Wi-Fi and location launch logging. Extensions read the snapshot
 * properties and push changes through the explicit update methods, so the mutable fields stay
 * owned here instead of being freely writable on [NeurhomeApplication].
 */
internal class ApplicationLoggingState {
    @Volatile
    private var currentWifiContext = WifiContext.UNKNOWN

    @Volatile
    private var wifiLoggingActive = false

    @Volatile
    private var positionLoggingActive = false

    @Volatile
    private var wifiNetworkCallback: NetworkCallback? = null

    val wifiContext: WifiContext
        get() = currentWifiContext

    val wifiLoggingEnabled: Boolean
        get() = wifiLoggingActive

    val positionLoggingEnabled: Boolean
        get() = positionLoggingActive

    val networkCallback: NetworkCallback?
        get() = wifiNetworkCallback

    fun setWifiLoggingEnabled(enabled: Boolean) {
        wifiLoggingActive = enabled
    }

    fun setPositionLoggingEnabled(enabled: Boolean) {
        positionLoggingActive = enabled
    }

    fun updateWifiContext(context: WifiContext) {
        currentWifiContext = context
    }

    fun setNetworkCallback(callback: NetworkCallback?) {
        wifiNetworkCallback = callback
    }
}
