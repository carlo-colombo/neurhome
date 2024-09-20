package ovh.litapp.neurhome3.ui.components

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
@Preview(backgroundColor = 0xf000, showBackground = true)
fun WatchPreview() {
    Neurhome3Theme {
        Watch(
            {},
            {
                Intent()
                    .putExtra(BatteryManager.EXTRA_LEVEL, 42)
                    .putExtra(BatteryManager.EXTRA_SCALE, 103)
            })
    }
}

@Composable
fun Watch(openAlarms: () -> Unit = {}, getBattery: () -> Intent? = { null }) {
    val now = produceState(initialValue = LocalDateTime.now(), producer = {
        while (true) {
            delay(5000)
            value = LocalDateTime.now()
        }
    })

    val batteryLevel = produceState(initialValue = 0f) {
        while (true) {
            value = getBattery()?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            } ?: 0f
            delay(60_000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.clickable { openAlarms() }) {
            Text(
                text = now.value.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayLarge
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        val dateTime = now.value.format(DateTimeFormatter.ofPattern("MMM d, y - 'w'w"))
        val tag = stringResource(id = R.string.suffix)
        val battery = batteryLevel.value.roundToInt()

        val components = listOfNotNull(
            if (tag != "") "[${tag}]" else null,
            dateTime,
            "${battery}%"
        )


        Text(
            text = components.joinToString(" - ")
        )
    }
}