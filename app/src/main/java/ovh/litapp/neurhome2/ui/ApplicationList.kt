package ovh.litapp.neurhome2.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome2.ui.theme.Neurhome2Theme

@Composable
fun ApplicationList(appsUiState: ApplicationsUiState, appsViewModel: ApplicationsViewModel) {
    Applications(appsUiState.apps, { appsViewModel.launch(it) })
}

private const val TAG = "ApplicationList"

@Composable
fun Applications(list: List<Application>, launchApp: (packageName: String) -> Unit) {
    Log.d(TAG, "Applications: " + list)
    LazyColumn{
        items(list){
            Row(modifier = Modifier.clickable {
                launchApp(it.packageName)
            }) {
                Text(text = it.label)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x000)
@Composable
fun ApplicationsPreview() {
    Neurhome2Theme {
        Surface(
            color = Color.Black
        ) {
            Applications(
                list = listOf(
                    Application("Fooasd", "net.fofvar"),
                    Application("Klarna", "com.klarna"),
                    Application("GoogLe", "foo.fofvar")
                ), {}
            )
        }
    }

}

@Composable
fun ph() {
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "All Apps",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}