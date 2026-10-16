package ovh.litapp.neurhome3.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val LOCATION_TAG = "LogPosition"
private const val MAX_CURRENT_LOCATION_AGE_MILLIS = 15 * 60 * 1000L
private const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 5_000.0

/** One-shot so a permanently unavailable geocoder cannot log once per launch. */
private val geocodeFailureLogged = AtomicBoolean(false)

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

    if (!shouldCaptureLocation(loggingState.positionLoggingEnabled, hasPermission)) return null

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
                        reportGeocodeFailure(errorMessage)
                        continuation.resume(null)
                    }
                }
            )
        } catch (e: Exception) {
            reportGeocodeFailure(e.message)
            continuation.resume(null)
        }
    }

/**
 * Logs the first geocoding failure only, leaving the existing "no city name" fallback intact.
 * Host JVM tests exercise only the pure helpers above, so this always finds a real `Log`.
 */
private fun reportGeocodeFailure(detail: String?) {
    if (geocodeFailureLogged.compareAndSet(false, true)) {
        Log.w(LOCATION_TAG, "Geocoding failed; logging the launch without a city name: $detail")
    }
}
