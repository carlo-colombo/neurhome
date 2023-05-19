@file:OptIn(ExperimentalFoundationApi::class)

package ovh.litapp.neurhome3.ui.components

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

@Composable
fun ApplicationsList(
    list: List<Application>,
    launchApp: (packageName: String) -> Unit,
    removeApp: (packageName: String) -> Unit,
    toggleVisibility: (packageName: String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = list, key = {
            it.packageName + it.label
        }) {
            ApplicationItem(it, launchApp, removeApp, toggleVisibility)
        }
    }
}


@Preview(showBackground = true, backgroundColor = 0x000)
@Composable
fun ApplicationsListPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)
    Neurhome3Theme {
        Surface(
            color = Color.Black
        ) {
            drawable?.let {
                ApplicationsList(
                    list = listOf(
                        Application("Fooasd foofasd asdoasod", "net.fofvar", icon = it),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                    ),
                    launchApp = {},
                    removeApp = {},
                    toggleVisibility = {  }
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