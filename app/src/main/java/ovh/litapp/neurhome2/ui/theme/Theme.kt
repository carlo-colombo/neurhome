package ovh.litapp.neurhome2.ui.theme

import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val Neurhome2Palette = lightColorScheme()

val Typography.appItem: TextStyle
    get() = TextStyle()

@Composable
fun Neurhome2Theme(backgroundAlpha: Float = 1.0f, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Neurhome2Palette
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(backgroundAlpha),
            contentColor = Color.White,
            content = content
        )
    }
}