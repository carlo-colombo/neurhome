package ovh.litapp.neurhome3.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun NeurhomeApplication.getPosition(): Location? {
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

suspend fun NeurhomeApplication.getCityName(location: Location): String? =
    suspendCoroutine { continuation ->
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull()?.locality)
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                }
            )
        } catch (_: Exception) {
            continuation.resume(null)
        }
    }
