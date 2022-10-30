package ovh.litapp.neurhome2

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ovh.litapp.neurhome2.Navigator.NavTarget
import ovh.litapp.neurhome2.ui.theme.Neurhome2Theme
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

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
                    NavigationComponent(navController = navController, navigator = Navigator)
                }
            }
        }
    }

    fun apps() {
        Log.d(TAG, "Loading apps")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { app ->
                val packageName = app.activityInfo.packageName
//                val iconData = getOrPutIcon(packageName)

                mapOf(
                    "label" to app.loadLabel(packageManager),
//                    "icon" to iconData,
                    "package" to packageName
                )
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

@Composable
fun NavigationComponent(navController: NavHostController, navigator: Navigator) {
    LaunchedEffect("navigation") {
        navigator.sharedFlow.onEach {
            navController.navigate(it.label)
        }.launchIn(this)
    }
    NavHost(
        navController = navController,
        startDestination = NavTarget.Home.label
    ) {
        composable(NavTarget.Home.label) {
            Home()
        }
        composable(NavTarget.ApplicationList.label) {
            ApplicationList()
        }
    }
}

@Composable
fun Home() {
    BackHandler(true) {}
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Watch()
        BottomBar()
    }
}

@Composable
fun ApplicationList() {
    val mutableState = remember { mutableStateOf(null) }


}

@Composable
fun Applications(applicationInfo: Array<ApplicationInfo>, packageManager: PackageManager) {
    Column() {
        applicationInfo.map {
            Row {
                Text(text = it.loadLabel(packageManager).toString())
                Text(text = it.packageName)
            }
        }
    }
}

fun makeApp(name: String): ApplicationInfo {
// PackageManager pm = Proxy.newProxyInstance(null, arrayOf(PackageManager::class.java), InvocationHandler() )
//
//
//
    var ai = object : ApplicationInfo() {
        override fun loadLabel(pm: PackageManager): CharSequence {
            return name
        }
    }

    ai.packageName = "com.example.${name}"

    return ai
}


//@Preview(showBackground = true, backgroundColor = 0x000)
//@Composable
//fun ApplicationsPreview() {
//    Applications(
//        applicationInfo = arrayOf(
//            makeApp("google"),
//            makeApp("telegram"),
//            makeApp("whatsapp"),
//        ),
//    )
//}

class NHApplicationInfo


@Composable
fun ph() {
    IconButton(onClick = { /*TODO*/ }) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "All Apps",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun BottomBar() {
    Row(
        horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
    ) {
        ph()
        ph()
        IconButton(onClick = { Navigator.navigateTo(NavTarget.ApplicationList) }) {
            Icon(
                Icons.Default.Apps,
                contentDescription = "All Apps",
                tint = Color.White
            )
        }
        ph()
        ph()
    }

}


@Preview(showBackground = true, backgroundColor = 0x000)
@Composable
fun DefaultPreview() {
    Neurhome2Theme {
        Surface(
            color = Color.Black,
            modifier = Modifier
                .height(600.dp)
                .width(400.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Watch()
                BottomBar()
            }
        }
    }
}

class NavigatorViewModel(
    private val navigator: Navigator,
) : ViewModel() {
    // Business logic
    fun somethingRelatedToBusinessLogic() { /* ... */
    }
}


object Navigator {
    private val _sharedFlow =
        MutableSharedFlow<NavTarget>(extraBufferCapacity = 1)
    val sharedFlow = _sharedFlow.asSharedFlow()

    fun navigateTo(navTarget: NavTarget) {
        _sharedFlow.tryEmit(navTarget)
    }

    enum class NavTarget(val label: String) {
        Home("home"),
        ApplicationList("applicationList")
    }
}
