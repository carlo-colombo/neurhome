package ovh.litapp.neurhome3.application

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.util.Log

const val TAG = "NeurhomeApplication.SSIDLogging"

internal fun NeurhomeApplication.enableSSIDLogging() {
    Log.d(TAG, "Enabling SSID Collection")

    if (cb == null) {
        Log.d(TAG, "Enabling SSID Collection (collect)")

        val request =
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

        cb = makeCallback()

        connectivityManager.requestNetwork(request, cb as NetworkCallback)

        Log.d(TAG, "Enabling SSID Collection (requestNetwork) $cb")
    }
}

internal fun NeurhomeApplication.disableSSIDLogging() {
    Log.d(TAG, "Disabling SSID Collection $cb")
    if (cb != null) connectivityManager.unregisterNetworkCallback(cb!!)
    cb = null
    ssid = null

    Log.d(TAG, "Disabled SSID Collection")
}

private val NeurhomeApplication.connectivityManager: ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

private fun NeurhomeApplication.makeCallback() =
    object : NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(
            network: Network, networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val wifiInfo = networkCapabilities.transportInfo as WifiInfo
            ssid = wifiInfo.ssid
            Log.d(TAG, "ssid: $ssid")
        }

        override fun onUnavailable() {
            super.onUnavailable()
            ssid = null
            Log.d(TAG, "wifi disconnected (unavailable)")
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            ssid = null
            Log.d(TAG, "wifi disconnected (lost)")
        }
    }