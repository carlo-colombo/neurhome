@file:OptIn(ExperimentalFoundationApi::class)

package ovh.litapp.neurhome3.ui.components

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

@Composable
fun ApplicationsList(
    list: List<Application>,
    appActions: INeurhomeViewModel.AppActions,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = list, key = {
            it.packageName + it.label + it.appInfo?.user
        }) { app ->
            ApplicationItem(
                app = app, appActions = appActions, manageEntry = true
            )
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
            color = Color.White
        ) {
            drawable?.let {
                ApplicationsList(
                    list = listOf(
                        Application(
                            "Fooasd foofasd asdoasod",
                            "net.fofvar",
                            icon = it,
                            intent = null
                        ),
                        Application("Fooasd", "net.fofvar.klarna", icon = it),
                        Application("Fooasd", "net.fofvar.barzot", icon = it),
                    ), appActions = INeurhomeViewModel.AppActions()
                )
            }
        }
    }

}
