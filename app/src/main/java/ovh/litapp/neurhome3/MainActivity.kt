package ovh.litapp.neurhome3

import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ovh.litapp.neurhome3.ui.NeurhomeMain
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme


private const val TAG = "NeurhomeMainActivity"

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Neurhome3Theme(backgroundAlpha = 0.1f) {
                Box(Modifier.safeContentPadding()) {
                    NeurhomeMain()
                }
            }
        }
    }
}


object Navigator {
    private val _sharedFlow = MutableSharedFlow<NavTarget>(extraBufferCapacity = 1)
    val sharedFlow = _sharedFlow.asSharedFlow()

    fun navigateTo(navTarget: NavTarget) {
        _sharedFlow.tryEmit(navTarget)
    }

    enum class NavTarget(val label: String) {
        Home("home"), ApplicationList("applicationList")
    }
}
