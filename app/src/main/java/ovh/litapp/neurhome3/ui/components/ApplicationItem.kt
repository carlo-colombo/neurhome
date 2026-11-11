package ovh.litapp.neurhome3.ui.components

import android.Manifest
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import ovh.litapp.neurhome3.R
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.avatar.DiceBearStyle
import ovh.litapp.neurhome3.data.avatar.contactAvatarSeed
import ovh.litapp.neurhome3.data.avatar.isContactRow
import ovh.litapp.neurhome3.data.stableListKey
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.components.dicebear.DiceBearAvatar
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.util.Locale

@Composable
@Preview(showBackground = true, backgroundColor = 0x000)
fun ApplicationPreview() {
    val drawable =
        AppCompatResources.getDrawable(LocalContext.current, R.drawable.ic_launcher_foreground)

    drawable?.let {
        Application(
            label = "NNeurhomeNeurhomeNeurhomeNeurhomeeurhome",
            packageName = "ovh.litapp.neurhome",
            icon = it,
            intent = null
        )
    }?.let { app ->
        Neurhome3Theme {
            Surface(
                color = Color.White
            ) {
                Column {
                    ApplicationItemComponent(
                        app = app,
                        callPermissionGranted = true,
                        onRequestCallPermission = {},
                        onLaunch = { _, _ -> },
                        open = true
                    )
                    HorizontalDivider()
                    ApplicationItemComponent(
                        app = app.copy(label = "New App", packageName = "io.github.neurhome"),
                        callPermissionGranted = true,
                        onRequestCallPermission = {},
                        onLaunch = { _, _ -> },
                        open = false
                    )

                    ApplicationVisibility.entries.forEach {
                        HorizontalDivider()
                        ApplicationItemComponent(
                            app = app.copy(
                                packageName = "veryveryverylong.packagepackageapage.namenamenamenamename",
                                visibility = it,
                                score = Math.random()
                            ),
                            callPermissionGranted = true,
                            onRequestCallPermission = {},
                            onLaunch = { _, _ -> },
                            open = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The CALL_PHONE state the application lists share. Each list used to remember its own state for
 * the same permission; callers that render [ApplicationItem] ask for it here instead.
 */
@Composable
@OptIn(ExperimentalPermissionsApi::class)
internal fun rememberCallPermissionState(): PermissionState =
    rememberPermissionState(Manifest.permission.CALL_PHONE)

/**
 * The style contact rows draw their generated avatar in, provided once around the whole app
 * (`MainActivity`).
 *
 * A [androidx.compose.runtime.CompositionLocal] rather than a settings flow collected per row: an
 * application list composes [ApplicationIcon] once per row and a contact list has hundreds of them,
 * so one collector at the root has to serve them all. The default draws no avatar, which keeps a
 * composition that is not the running app - a preview, an instrumented test driving one composable -
 * on the placeholder instead of resolving a scene against a context with no application.
 */
internal val LocalContactAvatarStyle = compositionLocalOf { DiceBearStyle.NONE }

/**
 * The row's icon: the application's launcher icon, or a contact's lazily decoded photo when the row
 * carries one, or - for a contact row with neither - a generated avatar seeded from the contact's
 * name, or the Person vector placeholder (also what style `none` asks for).
 *
 * A contact row's picture - the saved photo here, the generated avatar inside [DiceBearAvatar] - is
 * cut to a squircle ([SquircleShape]); a launcher icon keeps the shape it was drawn with. The photo
 * is cropped to fill that squircle, because a picture fitted into it would leave the shape showing
 * empty bands on the sides that are not square.
 *
 * [modifier] is applied to the drawable only: the Home rows weight the drawable so the label keeps
 * its 6f share of the row, and the placeholder has always been laid out unweighted. The generated
 * avatar replaces that placeholder, so it keeps the placeholder's unweighted [Modifier.size].
 */
@Composable
internal fun ApplicationIcon(
    app: Application,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Only the rows actually composed ask for a thumbnail; a contact row is built without one.
    val icon = app.icon ?: rememberContactPhoto(app.photoUri)
    val style = LocalContactAvatarStyle.current
    val avatarSeed = if (icon == null && style != DiceBearStyle.NONE) {
        contactAvatarSeed(app)
    } else {
        null
    }
    when {
        icon != null -> Image(
            painter = rememberDrawablePainter(drawable = icon),
            contentDescription = contentDescription,
            modifier = if (app.isContactRow) {
                modifier.size(size).clip(SquircleShape)
            } else {
                modifier.size(size)
            },
            contentScale = if (app.isContactRow) ContentScale.Crop else ContentScale.Fit,
        )

        avatarSeed != null -> DiceBearAvatar(
            seed = avatarSeed,
            style = style,
            size = size,
            // The avatar stands where the icon stood, so it carries that row's accessible name.
            modifier = contentDescription?.let { description ->
                Modifier.semantics { this.contentDescription = description }
            } ?: Modifier,
        )

        else -> Icon(
            painter = rememberVectorPainter(image = Icons.Default.Person),
            contentDescription = contentDescription,
            modifier = Modifier.size(size)
        )
    }
}

private val favouriteIconByPosition = mapOf(
    1 to Icons.Default.LooksOne,
    2 to Icons.Default.LooksTwo,
    3 to Icons.Default.Looks3,
    4 to Icons.Default.Looks4,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ApplicationItem(
    app: Application,
    permission: PermissionState,
    onLaunch: (Application?, track: Boolean) -> Unit,
    managementActions: INeurhomeViewModel.AppManagementActions = INeurhomeViewModel.AppManagementActions(),
    tagActions: INeurhomeViewModel.AppTagActions = INeurhomeViewModel.AppTagActions(),
    manageEntry: Boolean = false,
    availableTags: List<String> = emptyList(),
    compact: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    // The last set that was persisted, so a second edit-and-collapse persists again instead of
    // comparing the edited set against the value captured when the row first composed.
    var persistedTags by remember(app.stableListKey()) { mutableStateOf(app.tags.toSet()) }
    var editedTags by remember(app.stableListKey()) { mutableStateOf(persistedTags) }
    ApplicationItemComponent(
        app = app,
        callPermissionGranted = permission.status.isGranted,
        onRequestCallPermission = { permission.launchPermissionRequest() },
        onLaunch = onLaunch,
        managementActions = managementActions,
        tagActions = tagActions,
        open = open && manageEntry,
        onLongPress = {
            if (manageEntry) {
                if (open && editedTags != persistedTags) {
                    tagActions.setTags(app, editedTags)
                    persistedTags = editedTags
                }
                open = !open
            }
        },
        availableTags = availableTags,
        selectedTags = editedTags,
        onTagsChanged = { editedTags = it },
        compact = compact,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ApplicationItemComponent(
    app: Application,
    onLaunch: (Application?, track: Boolean) -> Unit,
    callPermissionGranted: Boolean,
    onRequestCallPermission: () -> Unit,
    managementActions: INeurhomeViewModel.AppManagementActions = INeurhomeViewModel.AppManagementActions(),
    tagActions: INeurhomeViewModel.AppTagActions = INeurhomeViewModel.AppTagActions(),
    open: Boolean = false,
    onLongPress: () -> Unit = {},
    availableTags: List<String> = emptyList(),
    selectedTags: Set<String> = app.tags.toSet(),
    onTagsChanged: (Set<String>) -> Unit = {},
    compact: Boolean = false,
) {
    val modifier = if (compact && !open) {
        Modifier
            .border(1.dp, color = Color.Transparent)
            .height(70.dp)
            .padding(horizontal = 5.dp, vertical = 4.dp)
    } else {
        Modifier
            .border(1.dp, color = if (open) Color.White else Color.Transparent)
            .padding(5.dp)
            .also { if (!open) it.height(50.dp) }
    }

    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = {
                    if (app.intent != null && !callPermissionGranted) {
                        onRequestCallPermission()
                    }

                    onLaunch(app, app.canTrack)
                }, onLongClick = onLongPress)
                .padding(vertical = if (compact) 1.dp else 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FitOrScrollLabel(
                text = app.label,
                style = if (compact) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                modifier = Modifier.weight(6f)
            )
            ApplicationIcon(
                app = app,
                size = if (compact) 54.dp else 50.dp,
                // Decorative: the adjacent label text already names the merged row.
                contentDescription = null,
                modifier = Modifier.weight(1f)
            )
        }
        if (open) {
            ApplicationManagement(app, managementActions, tagActions, availableTags, selectedTags, onTagsChanged)
        }
    }
}

@Composable
private fun ApplicationManagement(
    app: Application,
    managementActions: INeurhomeViewModel.AppManagementActions,
    tagActions: INeurhomeViewModel.AppTagActions,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
) {
    val stableKey = app.stableListKey()
    var alias by remember(stableKey) { mutableStateOf(app.alias) }
    var savedAlias by remember(stableKey) { mutableStateOf(app.alias) }
    var aliasWasFocused by remember(stableKey) { mutableStateOf(false) }
    val latestApp by rememberUpdatedState(app)

    // The field edits local state only; the alias is written once editing ends
    // (the field losing focus or this management panel leaving the composition).
    val persistAlias = {
        if (alias != savedAlias) {
            tagActions.setAlias(latestApp, alias)
            savedAlias = alias
        }
    }
    DisposableEffect(stableKey) {
        onDispose(persistAlias)
    }

    Text(text = "${app.packageName} (${String.format(Locale.ENGLISH, "%.2f", app.score)})")

    TextField(
        value = alias,
        onValueChange = { alias = it },
        label = { Text("Application alias") },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (aliasWasFocused && !focusState.isFocused) {
                    persistAlias()
                }
                aliasWasFocused = focusState.isFocused
            }
    )
    Spacer(Modifier.height(5.dp))

    TagSelector(
        availableTags = availableTags,
        selectedTags = selectedTags,
        onTagsChanged = {
            onTagsChanged(it)
        },
    )

    VisibilitySelector(modifier = Modifier.fillMaxWidth(),visibility = app.visibility) { vis -> managementActions.toggleVisibility(app, vis) }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            IconButton(onClick = { managementActions.remove(app) }) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Uninstall")
            }
        }
        Row {
            for (i in 1..4) {
                IconButton(onClick = { managementActions.setFavourite(app, i) }) {
                    Icon(imageVector = favouriteIconByPosition.getValue(i), "")
                }
            }
        }
    }
}

@Composable
private fun TagSelector(
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
) {
    Column {
        Text(text = "Tags", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            availableTags.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = {
                        onTagsChanged(if (tag in selectedTags) selectedTags - tag else selectedTags + tag)
                    },
                    label = { Text(tag) },
                )
            }
        }
    }
}
