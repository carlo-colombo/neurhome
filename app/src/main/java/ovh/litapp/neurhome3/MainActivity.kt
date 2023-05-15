package ovh.litapp.neurhome3

import android.content.Context
import android.os.*
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ovh.litapp.neurhome3.ui.ApplicationsViewModel
import ovh.litapp.neurhome3.ui.ApplicationsViewModelFactory
import ovh.litapp.neurhome3.ui.NeurhomeMain
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme


@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : ComponentActivity() {

    private val applicationViewModel: ApplicationsViewModel by viewModels {
        ApplicationsViewModelFactory(
            startActivity = ::startActivity,
            packageManager = packageManager,
            vibrate = ::vibrate,
            neurhomeRepository = (application as NeurhomeApplication).repository
        )
    }

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
                    NeurhomeMain(applicationViewModel)
                }
            }
        }
    }

    private fun vibrate() {
        val effectId = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (isPrimitiveSupported()) {
            vibratorManager.vibrate(
                CombinedVibration.createParallel(
                    VibrationEffect.startComposition().addPrimitive(effectId).compose()
                )
            )
        } else {
            Toast.makeText(
                this,
                "This primitive is not supported by this device.$effectId",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private fun isPrimitiveSupported(): Boolean {
        return vibratorManager.defaultVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
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
