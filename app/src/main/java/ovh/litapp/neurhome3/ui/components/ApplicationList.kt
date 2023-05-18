@file:OptIn(ExperimentalFoundationApi::class)

package ovh.litapp.neurhome3.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

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
            label = "NNeurhomeNeurhomeNeurhomeNeurhomeeurhome",
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.label,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(6f)
        )
        Icon(
            painter = rememberDrawablePainter(app.icon),
            contentDescription = app.label,
            tint = Color.Unspecified, // decorative element
            modifier = Modifier
                .size(50.dp)
                .weight(1f)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0x000)
@Composable
fun ApplicationsPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)
    Neurhome3Theme {
        Surface(
            color = Color.Black
        ) {
            drawable?.let {

                Applications(
                    list = listOf(
                        Application("Fooasd foofasd asdoasod", "net.fofvar", icon = it),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                    )
                )
            }
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