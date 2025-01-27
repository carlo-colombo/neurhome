package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import java.time.Duration


class CalendarRepository(
    checkPermission: (String) -> Boolean,
    settingsRepository: SettingsRepository,
    private val calendarDAO: CalendarDAO
) {

    val events = flow {
        while (true) {
            if (checkPermission(Manifest.permission.READ_CALENDAR)) {
                emit(calendarDAO.getNextEvents())
            } else {
                emit(listOf())
            }

            delay(Duration.ofMinutes(5).toMillis())
        }
    }.combine(settingsRepository.showCalendar) { events, showCalendar ->
       if (showCalendar) events else listOf()
    }
}
