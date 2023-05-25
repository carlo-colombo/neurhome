package ovh.litapp.neurhome3

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.data.SettingsRepository

class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            hiddenPackageDao = database.hiddenPackageDao(),
            settingDao = database.settingDao(),
            packageManager = packageManager,
            application = this,
            database = database
        )
    }

    val settingsRepository by lazy {
        SettingsRepository(
            settingDao = database.settingDao(), this::enableSSIDLogging, this::disableSSIDLogging
        )
    }

    init {
        applicationScope.launch {
            enableSSIDLogging()
        }
    }

    fun vibrate() {
        val effectId = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (isPrimitiveSupported(effectId)) {
            vibratorManager.vibrate(
                CombinedVibration.createParallel(
                    VibrationEffect.startComposition().addPrimitive(effectId).compose()
                )
            )
        } else {
            Toast.makeText(
                this,
                "This primitive is not supported by this device.$effectId",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    var ssid: String? = null
        private set

    private var cb: NetworkCallback? = null

    private suspend fun enableSSIDLogging() {
        Log.d(TAG, "Enabling SSID Collection")
        settingsRepository.wifiLogging
            .collect { isWifiLoggingEnabled ->
                if (isWifiLoggingEnabled && cb == null) {
                    Log.d(TAG, "Enabling SSID Collection (collect)")
                    val connectivityManager =
                        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                    val request =
                        NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .build()
                    cb = object :
                        NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                        override fun onCapabilitiesChanged(
                            network: Network, networkCapabilities: NetworkCapabilities
                        ) {
                            super.onCapabilitiesChanged(network, networkCapabilities)
                            val wifiInfo = networkCapabilities.transportInfo as WifiInfo
                            ssid = wifiInfo.ssid
                            Log.d(TAG, "ssid: ${ssid}")
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
                    connectivityManager.requestNetwork(request, cb as NetworkCallback)

                    Log.d(TAG, "Enabling SSID Collection (requestNetwork) $cb")
                }
            }
    }

    private fun disableSSIDLogging() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        Log.d(TAG, "Disabling SSID Collection $cb")
        if (cb != null) connectivityManager.unregisterNetworkCallback(cb!!)
        cb = null
        ssid = null

        Log.d(TAG, "Disabled SSID Collection")
    }

    fun getPosition(): Location? {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
    }

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private fun isPrimitiveSupported(effectId: Int): Boolean {
        return vibratorManager.defaultVibrator.areAllPrimitivesSupported(effectId)
    }
}