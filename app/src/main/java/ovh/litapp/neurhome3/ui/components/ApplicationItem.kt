package ovh.litapp.neurhome3.ui.components

import android.Manifest
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.Looks4
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
@Preview(showBackground = true, backgroundColor = 0x000)
fun ApplicationPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)

    val permission = object : PermissionState {
        override val permission: String
            get() = TODO("Not yet implemented")
        override val status: PermissionStatus
            get() = PermissionStatus.Granted

        override fun launchPermissionRequest() {
            TODO("Not yet implemented")
        }
    }

    drawable?.let {
        Application(
            label = "NNeurhomeNeurhomeNeurhomeNeurhomeeurhome",
            packageName = "ovh.litapp.neurhome",
            icon = it,
            intent = null,
        )
    }?.let { app ->
        Neurhome3Theme {
            Surface(
                color = Color.White
            ) {
                Column {
                    ApplicationItemComponent(
                        app = app,
                        appActions = INeurhomeViewModel.AppActions(),
                        permission = permission,
                        open = true
                    )
                    HorizontalDivider()
                    ApplicationItemComponent(
                        app = app.copy(label = "New App", packageName = "io.github.neurhome"),
                        appActions = INeurhomeViewModel.AppActions(),
                        permission = permission,
                        open = false
                    )

                    ApplicationVisibility.entries.forEach {
                        HorizontalDivider()
                        ApplicationItemComponent(
                            app = app.copy(
                                packageName = "veryveryverylong.packagepackageapage.namenamenamenamename",
                                visibility = it
                            ),
                            appActions = INeurhomeViewModel.AppActions(),
                            permission = permission,
                            open = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ApplicationItem(
    app: Application, appActions: INeurhomeViewModel.AppActions, manageEntry: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val permission = rememberPermissionState(
        permission = Manifest.permission.CALL_PHONE
    )

    ApplicationItemComponent(app, appActions, permission, open && manageEntry) {
        if(manageEntry) {
            open = !open
        }
    }
}

@Composable
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalGlideComposeApi::class,
    ExperimentalPermissionsApi::class
)
private fun ApplicationItemComponent(
    app: Application,
    appActions: INeurhomeViewModel.AppActions = INeurhomeViewModel.AppActions(),
    permission: PermissionState,
    open: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    val modifier = Modifier
        .border(1.dp, color = if (open) Color.White else Color.Transparent)
        .padding(5.dp)
        .also { if (!open) it.height(50.dp) }

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = {
                    if (app.intent != null) {
                        if (!permission.status.isGranted) {
                            permission.launchPermissionRequest()
                        }
                    }

                    appActions.launch(app, app.isVisible)
                }, onLongClick = onLongPress)
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
            if (app.icon != null) {
                GlideImage(
                    model = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(50.dp)
                        .weight(1f)
                )
            } else {
                Icon(
                    painter = rememberVectorPainter(image = Icons.Default.Person),
                    contentDescription = app.label,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
        if (open) {
            Text(text = "${app.packageName} (${String.format(Locale.ENGLISH, "%.2f", app.score)})")
            VisibilitySelector(app.visibility) { vis -> appActions.toggleVisibility(app, vis) }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()
            ) {
                Row {
                    IconButton(onClick = { appActions.remove(app) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Uninstall")
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

@Composable
@Preview
fun VisibilitySelector(visibility: ApplicationVisibility=ApplicationVisibility.VISIBLE, select: (ApplicationVisibility) -> Unit ={}) {
    SingleChoiceSegmentedButtonRow {
        ApplicationVisibility.entries.forEachIndexed { index, applicationVisibility ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ApplicationVisibility.entries.size
                ),
                selected = applicationVisibility == visibility,
                onClick = {select(applicationVisibility)},
                label = @Composable {
                    Icon(
                        imageVector = applicationVisibility.imageVector,
                        contentDescription = applicationVisibility.description
                    )
                }
            )
        }
    }
}
