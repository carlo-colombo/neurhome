package ovh.litapp.neurhome3.ui.components

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

@Composable
@Preview(backgroundColor = 0xf000, showBackground = true)
fun WatchPreview() {
    Neurhome3Theme {
        Watch {}
    }
}

@Composable
fun Watch(openAlarms: () -> Unit) {
    val now = produceState(initialValue = LocalDateTime.now(), producer = {
        while (true) {
            delay(5000)
            value = LocalDateTime.now()
        }
    })

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
        Text(
            text = now.value.format(DateTimeFormatter.ofPattern("MMM d, y - 'w'w")) +
                    stringResource(id = R.string.suffix)
        )
    }
}