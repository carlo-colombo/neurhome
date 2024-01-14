package ovh.litapp.neurhome3.ui.components

import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.Looks4
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ovh.litapp.neurhome3.ui.INeurhomeViewModel

@Preview
@Composable
fun ApplicationPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)

    val a = drawable?.let {
        Application(
            label = "NNeurhomeNeurhomeNeurhomeNeurhomeeurhome",
            packageName = "ovh.litapp.neurhome",
            icon = it,
        )
    }

    if (a != null) {
        ApplicationItem(
            app = a,
            appActions = INeurhomeViewModel.AppActions()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ApplicationItem(
    app: Application,
    appActions: INeurhomeViewModel.AppActions
) {
    var open by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .border(1.dp, color = if (open) Color.White else Color.Transparent)
            .padding(5.dp)
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = {
                    appActions.launch(app, app.isVisible)
                }, onLongClick = {
                    open = !open
                })
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
        if (open) {
            Text(text = "${app.label} (${app.count})")
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row {
                    IconButton(onClick = { appActions.remove(app) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Uninstall")
                    }
                    IconButton(onClick = { appActions.toggleVisibility(app) }) {
                        Icon(
                            imageVector = if (app.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Hide",
                        )
                    }
                }
                val icons = mapOf(
                    1 to Icons.Default.LooksOne,
                    2 to Icons.Default.LooksTwo,
                    3 to Icons.Default.Looks3,
                    4 to Icons.Default.Looks4,
                )

                Row {
                    for (i in 1..4) {
                        IconButton(onClick = { appActions.setFavourite(app, i) }) {
                            Icon(imageVector = icons[i]!!, "")
                        }
                    }
                }
            }
        }
    }
}