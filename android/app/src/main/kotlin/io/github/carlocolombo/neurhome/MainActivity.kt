package io.github.carlocolombo.neurhome

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle

import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import android.content.Intent.ACTION_DELETE
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat.startActivity
import android.provider.AlarmClock.ACTION_SET_ALARM
import com.progur.launcherassist.LauncherAssistPlugin.convertToBytes
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream

private const val CHANNEL = "neurhome.carlocolombo.github.io/removeApplication"

class MainActivity : FlutterActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeneratedPluginRegistrant.registerWith(this)

        MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "removeApplication" -> removeApplication(call, result)
                "openClock" -> openClock(result)
                "getWallpaper" -> getWallpaper(result)
                else -> result.notImplemented()
            }
        }
    }

    private fun openClock(result: Result) {
        val openClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        openClockIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(openClockIntent)

        result.success(null)
    }

    private fun removeApplication(call: MethodCall, result: Result) {
        val packageName = call.argument<String>("package")
        val intent = Intent(ACTION_DELETE)

        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)

        result.success(null)
    }

    private fun getWallpaper(result: MethodChannel.Result) {
        val wallpaperManager = WallpaperManager.getInstance(this.applicationContext)

        val wallpaperDrawable = wallpaperManager.drawable
        if (wallpaperDrawable is BitmapDrawable) {
            var wallpaperData = convertToBytes(wallpaperDrawable.bitmap,
                    Bitmap.CompressFormat.JPEG, 100)
            result.success(wallpaperData)
        }
    }
    fun convertToBytes(image: Bitmap, compressFormat: Bitmap.CompressFormat, quality: Int): ByteArray {
        val byteArrayOS = ByteArrayOutputStream()
        image.compress(compressFormat, quality, byteArrayOS)
        return byteArrayOS.toByteArray()
    }
}
