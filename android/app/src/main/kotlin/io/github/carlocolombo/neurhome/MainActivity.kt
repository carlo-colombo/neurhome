package io.github.carlocolombo.neurhome

import android.content.Intent
import android.os.Bundle

import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import android.content.Intent.ACTION_DELETE
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat.startActivity
import android.provider.AlarmClock.ACTION_SET_ALARM

class MainActivity : FlutterActivity() {
    private val CHANNEL = "neurhome.carlocolombo.github.io/removeApplication"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeneratedPluginRegistrant.registerWith(this)

        MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "removeApplication" -> {
                    val packageName = call.argument<String>("package")

                    print("From main activity: $packageName")

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
