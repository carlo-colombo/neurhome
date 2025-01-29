package ovh.litapp.neurhome3.ui.home

import android.app.AlarmManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.LauncherApps
import android.location.Location
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.Event
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.repositories.FavouritesRepository
import ovh.litapp.neurhome3.data.repositories.NeurhomeRepository
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModel

private const val TAG = "HomeViewModel"

interface IHomeViewModel : INeurhomeViewModel {
    fun push(s: String)
    fun clearQuery()
    fun pop()
    fun openAlarms()

    val vibrate: () -> Unit
    fun openCalendar(event: Event)
    val getBattery: () -> Intent?
}

class HomeViewModel(
    neurhomeRepository: NeurhomeRepository,
    favouritesRepository: FavouritesRepository,
    settingsRepository: SettingsRepository,
    calendarRepository: CalendarRepository,
    clockAlarmRepository: ClockAlarmRepository,
    val startActivity: (Intent) -> Unit,
    override val vibrate: () -> Unit,
    getSSID: () -> String?,
    getPosition: () -> Location?,
    launcherApps: LauncherApps,
    checkPermission: (String) -> Boolean,
    override val getBattery: () -> Intent?,
) : NeurhomeViewModel(
    neurhomeRepository,
    favouritesRepository,
    startActivity,
    getSSID,
    getPosition,
    launcherApps,
    checkPermission
), IHomeViewModel {
    private val query = MutableStateFlow<List<String>>(listOf())

    private val calendarState = combine(
        calendarRepository.events, settingsRepository.showCalendar, ::Pair
    )

    private val appsState = combine(
        neurhomeRepository.getTopApps(6),
        neurhomeRepository.applicationAndContacts,
        ::Pair
    )

    val homeUiState: StateFlow<HomeUiState> = combine(
        appsState,
        query,
        clockAlarmRepository.alarm,
    ) { (topApps, allApps), query, alarm ->
        val homeApps = if (query.isEmpty()) {
            topApps
        } else {
            Log.d(TAG, "Query: $query")
            val filter = Regex(
                buildString {
                    append(".*\\b(my)?")
                    append(query.joinToString(""))
                    append(".*")
                }, RegexOption.IGNORE_CASE
            )

            allApps.filter {
                it.visibility != ApplicationVisibility.HIDDEN_FROM_FILTERED && (filter matches it.label || filter matches (it.alias))

            }.sortedBy { -it.score }.take(6).reversed()
        }
        HomeUiState(
            allApps, query, homeApps, alarm, false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = HomeUiState()
    )

    val calendarUIState: StateFlow<CalendarUIState> = calendarState.map { (events, showCalendar) ->
        CalendarUIState(
            events, showCalendar, false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = CalendarUIState()
    )

    val favouriteUIState: StateFlow<FavouriteUIState> =
        favouritesRepository
            .favouriteApps
            .map { FavouriteUIState(it, false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = FavouriteUIState()
            )

    override fun push(s: String) {
        query.update { it + s }
    }

    override fun pop() {
        query.update {
            if (it.isNotEmpty()) it.take(it.size - 1)
            else it
        }
    }

    override fun clearQuery() {
        query.update { listOf() }
    }

    override fun launch(application: Application?, track: Boolean, query: String?) {
        super.launch(application, track, this.query.value.joinToString(""))
        clearQuery()
    }

    override fun openAlarms() {
        val openClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        openClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(openClockIntent)
    }

    override fun openCalendar(event: Event) {
        val eventID: Long = event.eventId
        val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID)
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.timestamp)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}

data class CalendarUIState(
    val events: List<Event> = listOf(),
    val showCalendar: Boolean = false,
    val loading: Boolean = true
)

data class HomeUiState(
    val allApps: List<Application> = listOf(),
    val query: List<String> = listOf(),
    var homeApps: List<Application> = listOf(),
    val alarm: AlarmManager.AlarmClockInfo? = null,
    val loading: Boolean = true,
)

data class FavouriteUIState(
    val apps: Map<Int, Application> = mapOf(),
    val loading: Boolean = true
)