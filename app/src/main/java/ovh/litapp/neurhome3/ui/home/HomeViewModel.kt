package ovh.litapp.neurhome3.ui.home

import android.app.AlarmManager
import android.content.ContentUris
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.data.repositories.CalendarRepository
import ovh.litapp.neurhome3.data.repositories.ClockAlarmRepository
import ovh.litapp.neurhome3.data.repositories.SettingsRepository
import ovh.litapp.neurhome3.data.repositories.TagRepository
import ovh.litapp.neurhome3.data.weather.WeatherRepository
import ovh.litapp.neurhome3.domain.home.MAX_HOME_APPLICATIONS
import ovh.litapp.neurhome3.domain.home.filterApplicationsForQuery
import ovh.litapp.neurhome3.domain.home.launchLogQuery
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModel
import ovh.litapp.neurhome3.ui.NeurhomeViewModelDependencies

interface IHomeViewModel : INeurhomeViewModel {
    fun push(s: String)
    fun clearQuery()
    fun pop()
    fun openAlarms()
    fun fetchWeather()
    fun fetchSun() {}
    fun refreshCalendar() {}

    val vibrate: () -> Unit
    fun openCalendar(event: Event)
    val getBattery: () -> Intent?

    /** Available tags, used to populate the application management panel opened by long press. */
    val tags: StateFlow<List<Tag>>
}

class HomeViewModel(
    dependencies: NeurhomeViewModelDependencies,
    settingsRepository: SettingsRepository,
    calendarRepository: CalendarRepository,
    clockAlarmRepository: ClockAlarmRepository,
    tagRepository: TagRepository,
    weatherRepository: WeatherRepository,
    override val vibrate: () -> Unit,
    getPositionForUi: () -> Location?,
    getCityName: suspend (Location) -> String?,
    override val getBattery: () -> Intent?,
) : NeurhomeViewModel(dependencies), IHomeViewModel {
    override val tags: StateFlow<List<Tag>> = tagRepository.tags.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val query = MutableStateFlow<List<String>>(listOf())

    private val filteredUiState: StateFlow<FilteredUIState> = combine(
        neurhomeRepository.applicationAndContacts,
        query,
    ) { allApps, query ->
        FilteredUIState(
            query, filterApplicationsForQuery(allApps, query), false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = FilteredUIState()
    )

    private val calendarRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val calendarUIState: StateFlow<CalendarUIState> = calendarRefresh.flatMapLatest {
        combine(
            calendarRepository.events, settingsRepository.showCalendar
        ) { events, showCalendar ->
            CalendarUIState(
                events, showCalendar, false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = CalendarUIState()
    )

    private val favouriteUIState: StateFlow<FavouriteUIState> =
        favouritesRepository.favouriteApps.map { FavouriteUIState(it, false) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = FavouriteUIState()
        )

    private val topUIState: StateFlow<TopUIState> =
        neurhomeRepository.getTopApps(
            n = MAX_HOME_APPLICATIONS,
            selection = settingsRepository.homeAppSelection,
            getWifiContext = getWifiContext,
            getPosition = getPositionForUi
        ).map { TopUIState(it, false) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = TopUIState()
        )

    /**
     * Next alarm and the alternative-time setting only: the wall clock itself is read locally by
     * `WatchArea`, so the once-a-minute tick no longer reaches the Home state tower.
     */
    private val watchAreaUIState: StateFlow<WatchAreaUIState> = combine(
        clockAlarmRepository.alarm,
        settingsRepository.showAlternativeTime,
        settingsRepository.alternativeTimeZone,
    ) { alarm, showAlternativeTime, timeZone ->
        WatchAreaUIState(
            nextAlarm = alarm,
            loading = false,
            showAlternativeTime = showAlternativeTime,
            alternativeTimeZone = timeZone,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = WatchAreaUIState()
        )

    private val weatherSun = WeatherSunHolder(
        scope = viewModelScope,
        weatherRepository = weatherRepository,
        clockAlarmRepository = clockAlarmRepository,
        getPosition = getPositionForUi,
        getCityName = getCityName,
    )

    private data class HomeContent(
        val favourite: FavouriteUIState,
        val calendar: CalendarUIState,
        val top: TopUIState,
        val filtered: FilteredUIState,
        val watchArea: WatchAreaUIState,
    )

    private val homeContent: Flow<HomeContent> = combine(
        favouriteUIState,
        calendarUIState,
        topUIState,
        filteredUiState,
        watchAreaUIState,
    ) { favourite, calendar, top, filtered, watchArea ->
        HomeContent(favourite, calendar, top, filtered, watchArea)
    }

    val homeUIState: StateFlow<HomeUIState> = combine(
        homeContent,
        weatherSun.weatherUIState,
        weatherSun.sunUIState,
    ) { content, weather, sun ->
        HomeUIState(
            favouriteUIState = content.favourite,
            calendarUIState = content.calendar,
            topUIState = content.top,
            filteredUiState = content.filtered,
            watchAreaUIState = content.watchArea,
            weatherUIState = weather,
            sunUIState = sun
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = HomeUIState()
    )

    init {
        fetchWeather()
        fetchSun()
    }

    override fun fetchWeather() = weatherSun.fetchWeather()

    override fun fetchSun() = weatherSun.fetchSun()

    override fun refreshCalendar() {
        calendarRefresh.update { it + 1 }
    }

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
        super.launch(application, track, launchLogQuery(query, this.query.value))
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
    val apps: List<Application> = listOf(),
    val loading: Boolean = false,
)

data class FavouriteUIState(
    val apps: Map<Int, Application> = mapOf(), val loading: Boolean = true
)

class TopUIState(
    val apps: List<Application> = listOf(), val loading: Boolean = true
)

data class WatchAreaUIState(
    val nextAlarm: AlarmManager.AlarmClockInfo? = null,
    val loading: Boolean = true,
    val showAlternativeTime: Boolean = false,
    val alternativeTimeZone: String = "",
)

data class HomeUIState(
    val favouriteUIState: FavouriteUIState = FavouriteUIState(),
    val calendarUIState: CalendarUIState = CalendarUIState(),
    val topUIState: TopUIState = TopUIState(),
    val filteredUiState: FilteredUIState = FilteredUIState(),
    val watchAreaUIState: WatchAreaUIState = WatchAreaUIState(),
    val weatherUIState: WeatherUIState = WeatherUIState(),
    val sunUIState: SunUIState = SunUIState()
)
