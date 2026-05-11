package ovh.litapp.neurhome3.data.repositories

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.ZonedDateTime

class ClockAlarmRepository(private val context: Context) {
    private val alarmService = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val alarm = flow {
        while (true) {
            emit(alarmService.nextAlarmClock)
            delay(Duration.ofMinutes(5).toMillis())
        }
    }

    val time = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(ZonedDateTime.now())
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
        trySend(ZonedDateTime.now())
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}
