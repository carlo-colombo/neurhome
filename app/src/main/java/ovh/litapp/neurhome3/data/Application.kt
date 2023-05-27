package ovh.litapp.neurhome3.data

import android.graphics.drawable.Drawable

data class Application(
    val label: String = "",
    val packageName: String = "",
    val icon: Drawable,
    val isVisible: Boolean = true,
    val count: Int = 0
)