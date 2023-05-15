@file:OptIn(ExperimentalFoundationApi::class)

package ovh.litapp.neurhome3.ui

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

@Composable
fun ApplicationList(appsUiState: ApplicationsUiState, appsViewModel: ApplicationsViewModel) {
    Applications(appsUiState.apps, appsViewModel::launch)
}

@Composable
fun Applications(list: List<Application>, launchApp: (packageName: String) -> Unit = {}) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = list, key = {
            it.packageName + it.label
        }) {
            ApplicationItem(it, launchApp)
        }
    }
}

@Preview
@Composable
fun ApplicationPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)

    val a = drawable?.let {
        Application(
            packageName = "ovh.litapp.neurhome",
            label = "Neurhome",
            icon = it
        )
    }

    if (a != null) {
        ApplicationItem(app = a, launchApp = fun(_: String) {})
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApplicationItem(
    app: Application,
    launchApp: (packageName: String) -> Unit
) {
    Row(
        modifier = Modifier
            .combinedClickable(onClick = {
                launchApp(app.packageName)
            })
            .fillMaxSize()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = app.label,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Icon(
            painter = rememberDrawablePainter(app.icon),
            contentDescription = app.label,
            tint = Color.Unspecified, // decorative element
            modifier = Modifier.size(50.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0x000)
@Composable
fun ApplicationsPreview() {
    Neurhome3Theme {
        Surface(
            color = Color.Black
        ) {
            Applications(
                list = listOf(
//                    Application("Fooasd", "net.fofvar", ),
//                    Application("Klarna", "com.klarna", packageManager.getApplicationIcon(app)),
//                    Application("GoogLe", "foo.fofvar", packageManager.getApplicationIcon(app))
                )
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
            modifier = Modifier.size(40.dp)
        )
    }
}