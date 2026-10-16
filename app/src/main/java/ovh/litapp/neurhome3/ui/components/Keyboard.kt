package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.home.FilteredUIState
import ovh.litapp.neurhome3.ui.home.IHomeViewModel

private const val KEYBOARD_ROW_HEIGHT = 59
private const val KEYCAP_HEIGHT = 55

/** Test tags become resource ids through `testTagsAsResourceId` for UiAutomator/Maestro. */
const val KeyboardTestTag = "keyboard"
const val KeyboardQueryTestTag = "keyboard-query"
const val KeyboardClearTestTag = "keyboard-clear"
const val KeyboardBackspaceTestTag = "keyboard-backspace"

fun keyboardKeyTestTag(key: String): String = "keyboard-key-$key"

/**
 * T9 group -> spoken label. Keycaps show the letter group, but a screen reader must also hear the
 * digit so the mapping is discoverable; "0-9" is the dedicated digit-pad key.
 */
private val KEY_ACCESSIBLE_LABELS = mapOf(
    "0-9" to "Digits 0 to 9",
    "abc" to "2, a, b, c",
    "def" to "3, d, e, f",
    "ghi" to "4, g, h, i",
    "jkl" to "5, j, k, l",
    "mno" to "6, m, n, o",
    "pqrs" to "7, p, q, r, s",
    "tuv" to "8, t, u, v",
    "wxyz" to "9, w, x, y, z",
)

/** Renders the bracket-token storage format (e.g. `[abc]`) as speakable text. */
private fun readableQuery(query: List<String>): String =
    query.joinToString(" ") { it.removeSurrounding("[", "]") }

private val KEYCAP_SHAPE = RoundedCornerShape(25f)

/** Translucent brushed-metal vertical gradient for borderless keycaps. */
private val KEYCAP_METAL_BRUSH = Brush.verticalGradient(
    0.0f to Color.White.copy(alpha = 0.45f),
    0.45f to Color(0xFFCFCFCF).copy(alpha = 0.22f),
    1.0f to Color(0xFF7A7A7A).copy(alpha = 0.28f),
)

@Composable
fun Keycap(
    onClick: () -> Unit = {},
    accessibilityLabel: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(KEYCAP_HEIGHT.dp)
            .clip(KEYCAP_SHAPE)
            .background(KEYCAP_METAL_BRUSH, KEYCAP_SHAPE)
            .semantics {
                if (accessibilityLabel != null) {
                    contentDescription = accessibilityLabel
                    // action = null adds the label without replacing the button's click action.
                    onClick(label = accessibilityLabel, action = null)
                }
            },
        shape = KEYCAP_SHAPE,
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
    ) {
        // The visible glyph must not be announced as well; the label above replaces it.
        Box(Modifier.clearAndSetSemantics { }) { content() }
    }
}

@Composable
fun Keyboard(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(KeyboardTestTag)
            .semantics { isTraversalGroup = true },
    ) {
        if (appsUiState.query.isNotEmpty()) {
            KeyboardInputRow(appsViewModel, appsUiState)
        }
        KeyboardFirstRow(appsViewModel)
        KeyboardSecondRow(appsViewModel)
    }
}

@Composable
private fun KeyboardInputRow(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = appsUiState.query.joinToString(""),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1f)
                .testTag(KeyboardQueryTestTag)
                .semantics {
                    contentDescription = readableQuery(appsUiState.query)
                    liveRegion = LiveRegionMode.Polite
                },
        )
        IconButton(
            onClick = {
                appsViewModel.clearQuery()
                appsViewModel.vibrate()
            },
            modifier = Modifier.testTag(KeyboardClearTestTag),
        ) {
            Icon(Icons.Default.Cancel, contentDescription = "Clear query")
        }
    }
}

@Composable
private fun KeyboardFirstRow(appsViewModel: IHomeViewModel) {
    KeyboardKeyRow(appsViewModel, listOf("0-9", "abc", "def", "ghi", "jkl"))
}

@Composable
private fun KeyboardSecondRow(appsViewModel: IHomeViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYBOARD_ROW_HEIGHT.dp),
    ) {
        listOf("mno", "pqrs", "tuv", "wxyz").forEach { key ->
            KeyboardKey(appsViewModel, key, Modifier.weight(1f))
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(2.dp)) {
            Keycap(
                onClick = {
                    appsViewModel.vibrate()
                    appsViewModel.pop()
                },
                accessibilityLabel = "Backspace",
                modifier = Modifier.testTag(KeyboardBackspaceTestTag),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }
        }
    }
}

@Composable
private fun KeyboardKeyRow(appsViewModel: IHomeViewModel, keys: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYBOARD_ROW_HEIGHT.dp),
    ) {
        keys.forEach { key ->
            KeyboardKey(appsViewModel, key, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyboardKey(
    appsViewModel: IHomeViewModel,
    key: String,
    modifier: Modifier,
) {
    Box(modifier = modifier.fillMaxHeight().padding(2.dp)) {
        Keycap(
            onClick = {
                appsViewModel.vibrate()
                appsViewModel.push("[$key]")
            },
            accessibilityLabel = KEY_ACCESSIBLE_LABELS[key] ?: key,
            modifier = Modifier.testTag(keyboardKeyTestTag(key)),
        ) {
            Text(key, style = MaterialTheme.typography.titleLarge)
        }
    }
}
