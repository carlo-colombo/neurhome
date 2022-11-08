package ovh.litapp.neurhome2.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Keycap(
    modifier: Modifier = Modifier, onClick: () -> Unit = {}, content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .border(1.dp, color = Color.White, shape = RoundedCornerShape(2.dp))
            .clickable {
                onClick()
            }, contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keyboard(appsViewModel: ApplicationsViewModel? = null) {
    Column(
        verticalArrangement = SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val key: @Composable (String) -> Unit = { s: String ->
            Keycap(
                modifier = Modifier
                    .weight(1f),
                onClick = {
                    appsViewModel?.push("[${s}]")
                }
            ) {
                Text(s)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            listOf("0-9", "abc", "def", "ghi", "jkl").forEach { key(it) }
        }
        Row {
            listOf("mno", "pqr", "tuv", "wxyz").forEach { key(it) }
            Keycap(modifier = Modifier.weight(1f), onClick = {
                appsViewModel?.pop()
            }) {
                Icon(
                    Icons.Default.Backspace, contentDescription = "All Apps"
                )
            }
        }
    }
}


@Composable
@Preview(backgroundColor = 0xf000)
fun KeyboardPreview() {
    Surface(
        color = Color.Black, contentColor = Color.White
    ) {
        Keyboard()
    }
}