package ovh.litapp.neurhome3.data.repositories

import android.app.AlarmManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ovh.litapp.neurhome3.NeurhomeApplication
import java.time.Duration

class ClockAlarmRepository (context: NeurhomeApplication){
    private val alarmService = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val alarm = flow {
        while (true) {
            emit(42)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }.map { alarmService.nextAlarmClock }
}