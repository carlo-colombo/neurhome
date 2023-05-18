package ovh.litapp.neurhome3.ui.components

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
@Preview( backgroundColor = 0xf000, showBackground = true)
fun WatchPreview() {
    Neurhome3Theme {
        Watch()
    }
}

@Composable
fun Watch() {
    val (value, setValue) = remember { mutableStateOf(LocalDateTime.now()) }

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                setValue(LocalDateTime.now())
                handler.postDelayed(this, 1000)
            }
        }

        handler.post(runnable)

        onDispose {
            handler.removeCallbacks(runnable)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value.format(DateTimeFormatter.ofPattern("MMM d, y - 'w'w")))
    }
}