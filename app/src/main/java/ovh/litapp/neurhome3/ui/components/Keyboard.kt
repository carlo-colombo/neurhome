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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.home.FilteredUIState
import ovh.litapp.neurhome3.ui.home.IHomeViewModel

private const val KEYBOARD_ROW_HEIGHT = 59
private const val KEYCAP_HEIGHT = 55

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
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(KEYCAP_HEIGHT.dp)
            .clip(KEYCAP_SHAPE)
            .background(KEYCAP_METAL_BRUSH, KEYCAP_SHAPE),
        shape = KEYCAP_SHAPE,
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
    ) {
        content()
    }
}

@Composable
fun Keyboard(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
            appsViewModel.clearQuery()
            appsViewModel.vibrate()
        }) {
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
            Keycap(onClick = {
                appsViewModel.vibrate()
                appsViewModel.pop()
            }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
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
        Keycap(onClick = {
            appsViewModel.vibrate()
            appsViewModel.push("[$key]")
        }) {
            Text(key, style = MaterialTheme.typography.titleLarge)
        }
    }
}
