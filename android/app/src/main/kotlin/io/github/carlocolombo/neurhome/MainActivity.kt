package io.github.carlocolombo.neurhome

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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

private const val CHANNEL = "neurhome.carlocolombo.github.io/main"
private const val TAG = "NeurhomeMainActivity"

class MainActivity : FlutterActivity() {
    private var wallpaperData: ByteArray? = null
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
                    "listApps" -> listApps(result)
                    "listTopApps" -> listApps(call, result)
                    "getWallpaper" -> getWallpaper(result)
                    "launchApp" -> launchApp(call, result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun listApps(call: MethodCall, result: MethodChannel.Result) {
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

            val iconData = icons.getOrPut(packageName) {
                Log.d(TAG, "converting icon into cache $packageName")
                val (time, result) = measureTimeMillisWithResult {
                    getBitmapFromDrawable(app.loadIcon(packageManager))?.let {
                        convertToBytes(
                            it,
                            Bitmap.CompressFormat.PNG, 100
                        )
                    }
                }
                Log.d(TAG, "convertToBytes: ${time}")
                result!!
            }


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

    private fun launchApp(call: MethodCall, result: MethodChannel.Result) {
        val packageName = call.argument<String>("packageName");
        packageManager.getLaunchIntentForPackage(packageName!!)?.let {
            context.startActivity(it)
        }
    }

    private fun listApps(result: MethodChannel.Result) {
        Log.d(TAG, "Loading apps, from cache:${icons.size}")
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val pm = packageManager
        val apps = pm
            .queryIntentActivities(intent, 0)
            .map { ri ->
                val packageName = ri.activityInfo.packageName
                val iconData = icons.getOrPut(packageName) {
                    Log.d(TAG, "converting icon, pushing into cache ${packageName}")
                    getBitmapFromDrawable(ri.loadIcon(pm))?.let {
                        convertToBytes(
                            it,
                            Bitmap.CompressFormat.PNG, 100
                        )
                    }!!
                }

                mapOf(
                    "label" to ri.loadLabel(pm),
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

    fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
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
        Log.d(TAG, "getBitmapFromDrawable ${time}")
        return result
    }

    fun convertToBytes(
        image: Bitmap,
        compressFormat: Bitmap.CompressFormat?,
        quality: Int
    ): ByteArray? {
        val byteArrayOS = ByteArrayOutputStream()
        image.compress(compressFormat, quality, byteArrayOS)
        return byteArrayOS.toByteArray()
    }

    @SuppressLint("MissingPermission")
    private fun getWallpaper(result: MethodChannel.Result) {
        wallpaperData?.let {
            result.success(it)
        } ?: run {
            val wallpaperManager = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            val wallpaperDrawable: Drawable = wallpaperManager.drawable
            if (wallpaperDrawable is BitmapDrawable) {
                wallpaperData = convertToBytes(
                    wallpaperDrawable.bitmap, Bitmap.CompressFormat.JPEG, 100
                )
                result.success(wallpaperData)
            }
        }
    }
}

fun <R> measureTimeMillisWithResult(block: () -> R): Pair<Long, R> {
    val start = System.currentTimeMillis()
    val result = block()
    return Pair(System.currentTimeMillis() - start, result)
}