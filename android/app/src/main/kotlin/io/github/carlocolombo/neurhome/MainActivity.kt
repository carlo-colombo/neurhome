package io.github.carlocolombo.neurhome

import android.content.Intent
import android.content.Intent.ACTION_DELETE
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import io.flutter.view.FlutterView

class MainActivity : FlutterActivity() {
    private val CHANNEL = "neurhome.carlocolombo.github.io/removeApplication"
    private val TAG = "NeurhomeMainActivity"

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
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
}
