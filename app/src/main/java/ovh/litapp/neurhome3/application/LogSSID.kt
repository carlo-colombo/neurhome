package ovh.litapp.neurhome3.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.util.Log
import androidx.core.app.ActivityCompat
import ovh.litapp.neurhome3.data.models.WifiContext
import ovh.litapp.neurhome3.data.models.classifyWifiNetworks
import ovh.litapp.neurhome3.data.models.classifyWifiNetworks

const val TAG = "NeurhomeApplication.SSIDLogging"

internal fun NeurhomeApplication.enableSSIDLogging() {
    Log.d(TAG, "Enabling Wi-Fi context collection")
    loggingState.setWifiLoggingEnabled(true)
    loggingState.updateWifiContext(WifiContext.UNKNOWN)

    if (loggingState.networkCallback == null) {
        val request =
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val callback = makeCallback()
        try {
            // This observes existing Wi-Fi networks without requesting the system to connect.
            connectivityManager.registerNetworkCallback(request, callback)
            loggingState.setNetworkCallback(callback)
        } catch (_: SecurityException) {
            loggingState.updateWifiContext(WifiContext.UNKNOWN)
            return
        }
    }

    refreshWifiContext()
}

internal fun NeurhomeApplication.disableSSIDLogging() {
    Log.d(TAG, "Disabling Wi-Fi context collection ${loggingState.networkCallback}")
    loggingState.setWifiLoggingEnabled(false)
    loggingState.updateWifiContext(WifiContext.UNKNOWN)
    loggingState.networkCallback?.let {
        try {
            connectivityManager.unregisterNetworkCallback(it)
        } catch (_: IllegalArgumentException) {
            // It may already have been unregistered by the system during shutdown.
        }
    }
    loggingState.setNetworkCallback(null)
}

private val NeurhomeApplication.connectivityManager: ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

internal fun NeurhomeApplication.canReadWifiContext(): Boolean {
    val hasLocationPermission = ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasLocationPermission) return false
    return try {
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    } catch (_: SecurityException) {
        false
    }
}

internal fun NeurhomeApplication.refreshWifiContext() {
    if (!loggingState.wifiLoggingEnabled || !canReadWifiContext()) {
        loggingState.updateWifiContext(WifiContext.UNKNOWN)
        return
    }

    try {
        val wifiCapabilities = connectivityManager.allNetworks.mapNotNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        }
        loggingState.updateWifiContext(
            classifyWifiNetworks(wifiCapabilities.map { (it.transportInfo as? WifiInfo)?.ssid })
        )
    } catch (_: SecurityException) {
        loggingState.updateWifiContext(WifiContext.UNKNOWN)
    }
}

private fun NeurhomeApplication.makeCallback() =
    object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onAvailable(network: Network) {
            refreshWifiContext()
        }

        override fun onCapabilitiesChanged(
            network: Network, networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            refreshWifiContext()
        }

        override fun onUnavailable() {
            super.onUnavailable()
            // Failure to observe the network is not proof that Wi-Fi is disconnected.
            loggingState.updateWifiContext(WifiContext.UNKNOWN)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            refreshWifiContext()
        }
    }
