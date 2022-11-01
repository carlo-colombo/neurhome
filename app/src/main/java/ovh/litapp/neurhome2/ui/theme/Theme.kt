package ovh.litapp.neurhome2.ui.theme

import androidx.compose.material.Surface
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Neurhome2Palette = lightColorScheme()

@Composable
fun Neurhome2Theme(backgroundAlpha: Float = 1.0f, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Neurhome2Palette
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(backgroundAlpha),
//            color = Color.Black.copy(backgroundAlpha),
//            contentColor = contentColorFor(Color.White),
            content = content
        )
    }
}