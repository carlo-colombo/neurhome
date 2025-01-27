package ovh.litapp.neurhome3.data.repositories

import android.app.AlarmManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Duration

class ClockAlarmRepository (alarmService: AlarmManager){

    val alarm = flow {
        while (true) {
            emit(alarmService.nextAlarmClock)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }
}