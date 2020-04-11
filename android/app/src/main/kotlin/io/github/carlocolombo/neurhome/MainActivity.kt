package io.github.carlocolombo.neurhome

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import com.progur.launcherassist.LauncherAssistPlugin
import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import io.flutter.view.FlutterView


class MainActivity : FlutterActivity() {
    private val CHANNEL = "neurhome.carlocolombo.github.io/removeApplication"
    private val TAG = "NeurhomeMainActivity"
    private val icons = HashMap<String, ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "starting application")
        GeneratedPluginRegistrant.registerWith(this)

        val view: FlutterView = flutterView
        view.setZOrderMediaOverlay(true)
        view.holder.setFormat(PixelFormat.TRANSPARENT)

        MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "removeApplication" -> {
                    val packageName = call.argument<String>("package")

                    Log.d(TAG, "remove application=$packageName")

                    val intent = Intent(ACTION_DELETE)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)

                    result.success(null)
                }
                "openClock" -> {
                    val openClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    openClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(openClockIntent)

                    result.success(null)
                }
                "listApps" -> {
                    Log.d(TAG, "Loading apps, from cache:${icons.size}")
                    val intent = Intent(Intent.ACTION_MAIN, null)
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)

                    val pm = packageManager
                    val apps = packageManager
                            .queryIntentActivities(intent, 0)
                            .map { ri ->
                                val packageName = ri.activityInfo.packageName
                                val iconData = icons.getOrPut(packageName, {
                                    Log.d(TAG, "converting icon into cache ${packageName}")
                                    val (time,result) = measureTimeMillisWithResult {
                                        LauncherAssistPlugin.convertToBytes(getBitmapFromDrawable(ri.loadIcon(pm)),
                                                Bitmap.CompressFormat.PNG, 100)
                                    }
                                    Log.d(TAG, "convertToBytes: ${time}")
                                    result
                                })

                                mapOf("label" to ri.loadLabel(pm),
                                        "icon" to iconData,
                                        "package" to packageName)
                            }
                    result.success(apps)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }


    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
        val (time, result) = measureTimeMillisWithResult {
            val bmp: Bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        Log.d(TAG, "getBitmapFromDrawable ${time}")
        return result
    }

}

fun <R> measureTimeMillisWithResult(block: () -> R): Pair<Long, R> {
    val start = System.currentTimeMillis()
    val result = block()
    return Pair(System.currentTimeMillis() - start, result)
}

