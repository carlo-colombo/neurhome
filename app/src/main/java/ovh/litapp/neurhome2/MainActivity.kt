package ovh.litapp.neurhome2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Surface
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ovh.litapp.neurhome2.ui.NeurhomeMain
import ovh.litapp.neurhome2.ui.theme.Neurhome2Theme

private const val TAG = "NeurhomeMainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            Neurhome2Theme {
                Surface(
                    color = Color.Black.copy(alpha = 0.1f)
                ) {
                    NeurhomeMain(
                        packageManager = packageManager,
                        startActivity = { startActivity(it) })
                }
            }
        }
    }


//    private fun getOrPutIcon(app: ApplicationInfo): ByteArray = icons.getOrPut(app.packageName) {
////        Log.d(TAG, "converting icon into cache ${app.packageName}")
////        val (time, result) =
////            getBitmapFromDrawable(packageManager.getApplicationIcon(app))?.let {
////                convertToBytes(
////                    it,
////                    Bitmap.CompressFormat.PNG, 100
////                )
////
////        }
////        Log.d(TAG, "convertToBytes: $time")
////        result!!
//    }
//
//    private fun getOrPutIcon(packageName: String): ByteArray =
//        getOrPutIcon(packageManager.getApplicationInfo(packageName, 0))
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
