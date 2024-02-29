package ovh.litapp.neurhome3.ui.home

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.CalendarRepository
import ovh.litapp.neurhome3.data.Event
import ovh.litapp.neurhome3.data.NeurhomeRepository
import ovh.litapp.neurhome3.data.SettingsRepository
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
}

class HomeViewModel(
    neurhomeRepository: NeurhomeRepository,
    settingsRepository: SettingsRepository,
    calendarRepository: CalendarRepository,
    val startActivity: (Intent) -> Unit,
    override val vibrate: () -> Unit,
    getSSID: () -> String?,
    getPosition: () -> Location?, launcherApps: LauncherApps,
) : NeurhomeViewModel(
    neurhomeRepository, startActivity, getSSID, getPosition, launcherApps
), IHomeViewModel {
    private val query = MutableStateFlow<List<String>>(listOf())

    private val calendarState = combine(
        calendarRepository.events,
        settingsRepository.showCalendar,
        ::Pair
    )

    private val appsState = combine(
        neurhomeRepository.getTopApps(6),
        neurhomeRepository.apps,
        neurhomeRepository.favouriteApps,
        ::Triple
    )

    val homeUiState: StateFlow<HomeUiState> = combine(
        appsState,
        query,
        calendarState,
    ) { (topApps, allApps, favourite), query, (events, showCalendar) ->
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

            allApps
                .filter { filter matches it.label && it.isVisible }
                .sortedBy { -it.count }
                .take(6)
                .reversed()
        }
        HomeUiState(
            allApps, query, homeApps, favourite, events, showCalendar, false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = HomeUiState()
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

    override fun launch(launcherActivityInfo: Application?, track: Boolean, query: String?) {
        super.launch(launcherActivityInfo, track, this.query.value.joinToString(""))
        clearQuery()
    }

    override fun openAlarms() {
        val openClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        openClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(openClockIntent)
    }

    override fun openCalendar(event: Event) {
        val eventID: Long = event.id
        val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventID)
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}

data class HomeUiState(
    val allApps: List<Application> = listOf(),
    val query: List<String> = listOf(),
    var homeApps: List<Application> = listOf(),
    val favouriteApps: Map<Int, Application> = mapOf(),
    val events: List<Event> = listOf(),
    val showCalendar: Boolean = false,
    val loading: Boolean = true
)
