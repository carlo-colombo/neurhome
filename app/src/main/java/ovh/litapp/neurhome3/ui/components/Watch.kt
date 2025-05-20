package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
@Preview(backgroundColor = 0xf000, showBackground = true)
fun WatchPreview() {
    Neurhome3Theme {
        Watch()
    }
}

@Composable
fun Watch(openAlarms: () -> Unit = {}, now: ZonedDateTime=ZonedDateTime.now()) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.clickable { openAlarms() }) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayLarge
            )
        }
    }
}