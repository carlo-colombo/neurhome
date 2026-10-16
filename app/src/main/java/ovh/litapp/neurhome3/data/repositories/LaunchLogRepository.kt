package ovh.litapp.neurhome3.data.repositories

import android.location.Location
import android.util.Log
import ch.hsr.geohash.GeoHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.MODEL_LOCATION_GEOHASH_PRECISION
import ovh.litapp.neurhome3.data.models.WifiContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "LaunchLogRepository"

/** Statistics simulation cap; keeps the heavy self-join query from materialising every row. */
private const val SIMULATED_TOP_APPS_SQL_LIMIT = 100

/**
 * Every read and write of the launch log: recording a launch, the version counter the ranking
 * caches key on, the simulated statistics read, and the known SSIDs. The log is the input of both
 * the classic and the learned ranking, so a write here is what tells the ranking to reconsider its
 * model.
 */
class LaunchLogRepository(
    private val applicationLogEntryDao: ApplicationLogEntryDao,
    private val scope: CoroutineScope,
    /** Invoked after a launch landed, so the home ranking can decide whether to retrain. */
    private val onLaunchLogged: () -> Unit = {},
) {
    private val launchLogVersionState = MutableStateFlow(0L)

    /**
     * Launch-log version, bumped on every insert. The ranking reads are computed from the log, so
     * they are cached against this version and recomputed when a launch lands instead of on a timer.
     */
    val launchLogVersion: StateFlow<Long> = launchLogVersionState

    fun logLaunch(
        packageName: String,
        user: Int,
        wifiContext: WifiContext,
        position: Location?,
        query: String? = null
    ) {
        Log.d(TAG, "logLaunch: $packageName:${wifiContext.state}:hasLocation=${position != null}")

        scope.launch(Dispatchers.IO) {
            applicationLogEntryDao.insert(
                ApplicationLogEntry(
                    packageName = packageName,
                    timestamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
                        .format(Instant.now()),
                    wifi = wifiContext.ssid,
                    wifiState = wifiContext.state,
                    latitude = position?.latitude,
                    longitude = position?.longitude,
                    geohash = if (position != null) GeoHash.withCharacterPrecision(
                        position.latitude, position.longitude, MODEL_LOCATION_GEOHASH_PRECISION
                    ).toBase32() else null,
                    user = user,
                    query = query
                )
            )
            launchLogVersionState.update { it + 1 }
            onLaunchLogged()
        }
    }

    /**
     * The simulated statistics ranking's launch counts, unresolved. An intent-only row is logged
     * under its call URI, which only the loaded starred-contact rows can turn back into a row, and
     * this log-only collaborator does not hold them; the caller resolves the keys it reads here.
     */
    fun simulatedTopApps(
        dayOfWeek: Int,
        minuteOfDay: Int,
        wifi: String?,
    ): Flow<List<HomeAppCandidate>> = flow {
        emit(
            applicationLogEntryDao
                .simulatedTopApps(
                    dayOfWeek,
                    minuteOfDay,
                    wifi,
                    limit = SIMULATED_TOP_APPS_SQL_LIMIT
                )
                .map { candidate ->
                    HomeAppCandidate(
                        packageName = candidate.packageName,
                        user = candidate.user,
                        score = candidate.score,
                    )
                }
        )
    }.flowOn(Dispatchers.IO)

    fun getUniqueSSIDs() = flow {
        emit(applicationLogEntryDao.getUniqueSSIDs())
    }.flowOn(Dispatchers.IO)
}
