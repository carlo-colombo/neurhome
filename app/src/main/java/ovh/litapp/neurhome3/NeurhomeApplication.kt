package ovh.litapp.neurhome3

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.CalendarRepository
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.data.SettingsRepository
import java.io.FileOutputStream


@RequiresApi(Build.VERSION_CODES.S)
class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    private val database by lazy {
        AppDatabase.getDatabase(this)
    }
    val repository by lazy {
        NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            hiddenPackageDao = database.hiddenPackageDao(),
            settingDao = database.settingDao(),
            packageManager = packageManager,
            application = this,
            database = database,
            launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        )
    }

    val settingsRepository by lazy {
        SettingsRepository(
            settingDao = database.settingDao(), this::enableSSIDLogging, this::disableSSIDLogging
        )
    }

    val calendarRepository by lazy {
        CalendarRepository(this)
    }

    init {
        applicationScope.launch {
            enableSSIDLogging()
        }
    }

    fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val aa = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()


            vibratorManager.vibrate(
                CombinedVibration.createParallel(
                    VibrationEffect.createOneShot(
                        50,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                ), VibrationAttributes.Builder(aa).build()
            )
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

    val launcherApps: LauncherApps by lazy {
        getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }

    fun replaceDatabase(u: Uri) {
        this.database.close()
        this.contentResolver.openInputStream(u)?.use {
            it.copyTo(
                FileOutputStream(this@NeurhomeApplication.getDatabasePath(NEURHOME_DATABASE))
            )
        }
    }
}