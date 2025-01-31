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
import ovh.litapp.neurhome3.data.models.Event
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

    private val filteredUiState: StateFlow<FilteredUIState> = combine(
        neurhomeRepository.applicationAndContacts,
        query,
    ) { allApps, query ->
        val filteredApps = if (query.isNotEmpty()) {
            Log.d(TAG, "Query: $query")
            val filter = Regex(
                buildString {
                    append(".*\\b(my)?")
                    append(query.joinToString(""))
                    append(".*")
                }, RegexOption.IGNORE_CASE
            )

            allApps
                .filter {
                    it.visibility != ApplicationVisibility.HIDDEN_FROM_FILTERED
                            && (filter matches it.label
                            || filter matches it.alias)
                }
                .sortedBy { -it.score }
                .take(6)
                .reversed()
        } else {
            listOf()
        }

        FilteredUIState(
            query, filteredApps, false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = FilteredUIState()
    )

    private val calendarUIState: StateFlow<CalendarUIState> = combine(
        calendarRepository.events, settingsRepository.showCalendar
    ) { events, showCalendar ->
        CalendarUIState(
            events, showCalendar, false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = CalendarUIState()
    )

    private val favouriteUIState: StateFlow<FavouriteUIState> =
        favouritesRepository
            .favouriteApps
            .map { FavouriteUIState(it, false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = FavouriteUIState()
            )

    private val topUIState: StateFlow<TopUIState> = neurhomeRepository.getTopApps(6)
        .map { TopUIState(it, false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = TopUIState()
        )

    private val alarmUIState: StateFlow<AlarmUIState> =
        clockAlarmRepository
            .alarm
            .map { AlarmUIState(it, false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = AlarmUIState()
            )

    val homeUIState: StateFlow<HomeUIState> = combine(
        favouriteUIState,
        calendarUIState,
        topUIState,
        filteredUiState,
        alarmUIState,
        ::HomeUIState
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = HomeUIState()
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

data class FilteredUIState(
    val query: List<String> = listOf(),
    var apps: List<Application> = listOf(),
    val loading: Boolean = true,
)

data class FavouriteUIState(
    val apps: Map<Int, Application> = mapOf(),
    val loading: Boolean = true
)

class TopUIState(
    val apps: List<Application> = listOf(),
    val loading: Boolean = true
)

data class AlarmUIState(
    val next: AlarmManager.AlarmClockInfo? = null,
    val loading: Boolean = true
)

data class HomeUIState(
    val favouriteUIState: FavouriteUIState = FavouriteUIState(),
    val calendarUIState: CalendarUIState = CalendarUIState(),
    val topUIState: TopUIState = TopUIState(),
    val filteredUiState: FilteredUIState = FilteredUIState(),
    val alarmUIState: AlarmUIState = AlarmUIState()
)