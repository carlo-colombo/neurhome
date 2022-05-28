package io.github.carlocolombo.neurhome

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode.transparent
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private const val CHANNEL = "neurhome.carlocolombo.github.io/main"
private const val TAG = "NeurhomeMainActivity"

class MainActivity : FlutterActivity() {
    private val icons = HashMap<String, ByteArray>()

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        intent.putExtra("background_mode", transparent.toString())

        Log.d(TAG, "starting application")

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "removeApp" -> removeApplication(call, result)
                    "openClock" -> openClock(result)
                    "listApps" -> listAllApps(result)
                    "listTopApps" -> listTopApps(call, result)
                    "launchApp" -> launchApp(call, result)
                    "getPackagesIcons" -> getPackagesIcons(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun listTopApps(call: MethodCall, result: MethodChannel.Result) {
        var count = call.argument<Int>("count") ?: 6
        var i = 0
        var topAppsInfo = ArrayList<Map<String, Any>>()
        val topApps =
            call.argument<List<Map<String, Any>>>("apps") ?: return result.success(topAppsInfo)

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val appsMap = packageManager
            .queryIntentActivities(intent, 0)
            .associateBy { it.activityInfo.packageName }

        while (count > 0 && i < topApps.size && appsMap.isNotEmpty()) {
            val packageName = topApps[i++]["package"] as String
            val app = appsMap[packageName] ?: continue

            Log.d(TAG, "Found '$packageName', remaining apps: ${count - 1}")
            count--

            val iconData = getOrPutIcon(packageName)

            topAppsInfo.add(
                mapOf(
                    "label" to app.loadLabel(packageManager),
                    "icon" to iconData,
                    "package" to packageName
                )
            )
        }
        result.success(topAppsInfo)
    }

    private fun getPackagesIcons(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        val packages = call.argument<List<String?>>("packages");
        result.success(packages?.map { it?.let { getOrPutIcon(it) } })
    }

    private fun getOrPutIcon(packageName: String): ByteArray {
        return icons.getOrPut(packageName) {
            Log.d(TAG, "converting icon into cache $packageName")
            val (time, result) = measureTimeMillisWithResult {
                getBitmapFromDrawable(packageManager.getApplicationIcon(packageName))?.let {
                    convertToBytes(
                        it,
                        Bitmap.CompressFormat.PNG, 100
                    )
                }
            }
            Log.d(TAG, "convertToBytes: ${time}")
            result!!
        }
    }

    private fun launchApp(call: MethodCall, result: MethodChannel.Result) {
        val packageName = call.argument<String>("packageName")!!;

        packageManager
            .getLaunchIntentForPackage(packageName)?.let {
                context.startActivity(it)
            }
    }

    private fun listAllApps(result: MethodChannel.Result) {
        Log.d(TAG, "Loading apps, from cache:${icons.size}")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { app ->
                val packageName = app.activityInfo.packageName
                val iconData = getOrPutIcon(packageName)

                mapOf(
                    "label" to app.loadLabel(packageManager),
                    "icon" to iconData,
                    "package" to packageName
                )
            }

        result.success(apps)
    }

    private fun openClock(result: MethodChannel.Result) {
        val openClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        openClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(openClockIntent)

        result.success(null)
    }

    private fun removeApplication(call: MethodCall, result: MethodChannel.Result) {
        val packageName = call.argument<String>("package")

        Log.d(TAG, "remove application=$packageName")

        val intent = Intent(ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)

        result.success(null)
    }


    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
        val (time, result) = measureTimeMillisWithResult {
            val bmp: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        Log.d(TAG, "getBitmapFromDrawable $time")
        return result
    }

    private fun convertToBytes(
        image: Bitmap,
        compressFormat: Bitmap.CompressFormat?,
        quality: Int
    ): ByteArray? {
        val byteArrayOS = ByteArrayOutputStream()
        image.compress(compressFormat, quality, byteArrayOS)
        return byteArrayOS.toByteArray()
    }
}

fun <R> measureTimeMillisWithResult(block: () -> R): Pair<Long, R> {
    val start = System.currentTimeMillis()
    val result = block()
    return Pair(System.currentTimeMillis() - start, result)
}