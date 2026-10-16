package ovh.litapp.neurhome3.ui.home

import android.app.AlarmManager
import android.content.Intent
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.data.models.Tag
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.Watch
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val alarmTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, y 'w'w")
private val alternativeTimeFormatter = DateTimeFormatter.ofPattern("HH:mm z")

private const val MILLIS_PER_MINUTE = 60_000L

/** Milliseconds until the next wall-clock minute, the boundary `ACTION_TIME_TICK` fires on. */
internal fun millisUntilNextMinute(nowMillis: Long): Long =
    MILLIS_PER_MINUTE - nowMillis % MILLIS_PER_MINUTE

/**
 * Wall clock for the watch face, ticking on the minute inside the composable. The Home tree used to
 * receive a fresh [WatchAreaUIState] from `ACTION_TIME_TICK` every minute, which recombined
 * `homeUIState` and recomposed the whole dashboard; the clock is the only consumer of the minute,
 * so it keeps its own tick and the rest of Home is left alone.
 */
@Composable
private fun rememberMinuteTick(): State<ZonedDateTime> =
    produceState(initialValue = ZonedDateTime.now()) {
        while (true) {
            delay(millisUntilNextMinute(System.currentTimeMillis()))
            value = ZonedDateTime.now()
        }
    }

@Composable
internal fun ColumnScope.WatchArea(
    watchAreaUIState: WatchAreaUIState,
    viewModel: IHomeViewModel
) {
    val systemZone = ZoneId.systemDefault()
    val now by rememberMinuteTick()
    val alarm = remember(watchAreaUIState.nextAlarm, systemZone) {
        watchAreaUIState.nextAlarm?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.triggerTime), systemZone)
                .format(alarmTimeFormatter)
        }
    }

    val batteryLevel = produceState(initialValue = 0f) {
        while (true) {
            value = viewModel.getBattery()?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            } ?: 0f
            delay(10_000)
        }
    }

    val alternativeTime = remember(
        now, watchAreaUIState.showAlternativeTime, watchAreaUIState.alternativeTimeZone
    ) {
        if (watchAreaUIState.showAlternativeTime && watchAreaUIState.alternativeTimeZone != "") {
            now.withZoneSameInstant(ZoneId.of(watchAreaUIState.alternativeTimeZone))
        } else null
    }

    val date = remember(now) {
        now.format(dateFormatter)
    }
    val battery = batteryLevel.value.roundToInt()

    val componentsText = remember(date, battery, alternativeTime) {
        listOfNotNull(
            date,
            "${battery}%",
            alternativeTime?.format(alternativeTimeFormatter)
        ).joinToString(" | ")
    }

    val tag = stringResource(id = R.string.suffix)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val blockStyle = { it: Float ->
            Modifier.Companion
                .weight(it)
        }
        Row {
            Column(modifier = blockStyle(0.25f)) {
                if(tag.isNotEmpty()) {
                    Text(text = tag)
                }
            }
            Box(modifier = blockStyle(0.75f)) {
                Watch(viewModel::openAlarms, now)
            }
            Column(modifier = blockStyle(0.25f)) {
                if (alarm != null) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.Companion.fillMaxWidth()
                    ) {
                        Text(text = alarm)
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Next alarm time"
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text(
                text = componentsText
            )
        }
    }
}

@Preview(backgroundColor = 0xf000, showBackground = true)
@Composable
fun WatchAreaPreview() {
    Neurhome3Theme {
        Column {
            WatchArea(
                watchAreaUIState = WatchAreaUIState(
                    nextAlarm = AlarmManager.AlarmClockInfo(System.currentTimeMillis(), null),
                    showAlternativeTime = true,
                    alternativeTimeZone = "Europe/Dublin",
                ),
                viewModel = object : IHomeViewModel {
                    override fun push(s: String) {
                        TODO("Not yet implemented")
                    }

                    override fun clearQuery() {
                        TODO("Not yet implemented")
                    }

                    override fun pop() {
                        TODO("Not yet implemented")
                    }

                    override fun openAlarms() {
                        TODO("Not yet implemented")
                    }

                    override fun fetchWeather() {
                        TODO("Not yet implemented")
                    }

                    override val vibrate: () -> Unit
                        get() = TODO("Not yet implemented")

                    override fun openCalendar(event: Event) {
                        TODO("Not yet implemented")
                    }

                    override val getBattery: () -> Intent?
                        get() = {
                            Intent()
                                .putExtra(BatteryManager.EXTRA_LEVEL, 88)
                                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                        }
                    override val appActions: INeurhomeViewModel.AppActions
                        get() = TODO("Not yet implemented")
                    override val tags: StateFlow<List<Tag>> = MutableStateFlow(emptyList())
                }
            )
        }
    }
}