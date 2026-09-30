package ovh.litapp.neurhome3.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val MAX_CURRENT_LOCATION_AGE_MILLIS = 15 * 60 * 1000L
private const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 5_000.0

internal fun shouldCaptureLocation(loggingEnabled: Boolean, hasPermission: Boolean): Boolean =
    loggingEnabled && hasPermission

internal fun isUsableLocationFix(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
    ageMillis: Long
): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyMeters.isFinite() && accuracyMeters > 0.0 &&
        accuracyMeters <= MAX_CURRENT_LOCATION_ACCURACY_METERS &&
        ageMillis in 0..MAX_CURRENT_LOCATION_AGE_MILLIS

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
    return try {
        lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/** Returns a recent, usable foreground fix only when launch-location logging is enabled. */
fun NeurhomeApplication.getPositionForLogging(): Location? {
    val hasPermission = ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!shouldCaptureLocation(positionLoggingEnabled, hasPermission)) return null

    val location = getPosition() ?: return null
    val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
    if (ageNanos < 0) return null
    val ageMillis = ageNanos / 1_000_000L
    if (!location.hasAccuracy() || !isUsableLocationFix(
            location.latitude,
            location.longitude,
            location.accuracy.toDouble(),
            ageMillis
        )
    ) return null

    return location
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
