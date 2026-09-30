package ovh.litapp.neurhome3.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFeatureTest {
    @Test
    fun coarsensHistoricalGeohashesAndKeepsMissingLocationUnknown() {
        val entry = ApplicationLogEntry(
            packageName = "com.example.app",
            timestamp = "2026-09-30T12:00:00",
            wifi = null,
            latitude = 37.4,
            longitude = -122.1,
            geohash = "9q9hvu123456",
            user = 0
        )

        assertEquals("9q9hv", entry.coarsenedLocationFeature())
        assertNull(entry.copy(geohash = null).coarsenedLocationFeature())
    }
}
