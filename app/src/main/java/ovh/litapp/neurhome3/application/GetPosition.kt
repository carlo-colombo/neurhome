package ovh.litapp.neurhome3.application

import android.annotation.SuppressLint
import android.location.Location
import com.google.android.gms.location.LocationServices

@SuppressLint("MissingPermission")
fun NeurhomeApplication.getPosition(): Location? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    var location: Location? = null
    fusedLocationClient.lastLocation.addOnSuccessListener {
        location = it
    }
    return location
}
