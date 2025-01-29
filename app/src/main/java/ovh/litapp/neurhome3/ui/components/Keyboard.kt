package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.ui.home.FilteredUIState
import ovh.litapp.neurhome3.ui.home.IHomeViewModel

private const val TAG = "Keyboard"

@Composable
fun Keycap(
    onClick: () -> Unit = {},
    border: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        Modifier
            .fillMaxWidth()
            .height(55.dp),
        shape = RoundedCornerShape(25f),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.outlinedButtonColors(),
        border = if (border) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        ) else null
    ) {
        content()
    }
}

@Composable
fun Keyboard(
    appsViewModel: IHomeViewModel,
    appsUiState: FilteredUIState
) {
    Column() {
        val modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(2.dp)
        val key: @Composable (String) -> Unit = { s: String ->
            Box(modifier = modifier) {
                Keycap(onClick = {
                    appsViewModel.vibrate()
                    appsViewModel.push("[${s}]")
                }) {
                    Text(s, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .weight(1f, true),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = appsUiState.query.joinToString(""),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f, true)
            )
            if (appsUiState.query.isNotEmpty()) {
                IconButton(onClick = {
                    appsViewModel.clearQuery()
                    appsViewModel.vibrate()
                }) {
                    Icon(
                        Icons.Default.Cancel, contentDescription = "Clear query"
                    )
                }
            }
        }
        Row(modifier.weight(1f)) {
            listOf("0-9", "abc", "def", "ghi", "jkl").forEach { key(it) }
        }
        Row(modifier.weight(1f)) {
            listOf("mno", "pqrs", "tuv", "wxyz").forEach { key(it) }
            Box(
                modifier = modifier
            ) {
                Keycap(border = false, onClick = {
                    appsViewModel.vibrate()
                    appsViewModel.pop()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace"
                    )
                }
            }
        }
    }
}
