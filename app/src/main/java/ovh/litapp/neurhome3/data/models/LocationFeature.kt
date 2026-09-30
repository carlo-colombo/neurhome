package ovh.litapp.neurhome3.data.models

const val MODEL_LOCATION_GEOHASH_PRECISION = 5

/** Returns the only location value suitable for model features, including for legacy hashes. */
fun ApplicationLogEntry.coarsenedLocationFeature(): String? =
    geohash?.take(MODEL_LOCATION_GEOHASH_PRECISION)?.takeIf(String::isNotBlank)
