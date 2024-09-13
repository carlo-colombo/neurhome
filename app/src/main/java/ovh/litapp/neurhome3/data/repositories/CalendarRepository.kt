package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.data.dao.CalendarDAO
import java.time.Duration


class CalendarRepository(val context: NeurhomeApplication, private val calendarDAO: CalendarDAO) {

    val events = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.combine(context.settingsRepository.showCalendar) { _, showCalendar ->
        if (!showCalendar || !context.checkPermission(Manifest.permission.READ_CALENDAR)) {
            return@combine listOf()
        }

        calendarDAO.getNextEvents()
    }
}
