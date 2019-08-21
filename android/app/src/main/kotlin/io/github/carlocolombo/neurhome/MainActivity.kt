package io.github.carlocolombo.neurhome

import android.content.Intent
import android.os.Bundle

import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugins.GeneratedPluginRegistrant
import android.content.Intent.ACTION_DELETE
import android.net.Uri


class MainActivity: FlutterActivity() {
  private val CHANNEL = "neurhome.carlocolombo.github.io/removeApplication"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    GeneratedPluginRegistrant.registerWith(this)

    MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
      if (call.method == "removeApplication") {
        val packageName = call.argument<String>("package")

        print("From main activity: $packageName")

        val intent = Intent(ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)

        result.success(null)

      } else {
        result.notImplemented()
      }
    }
  }
}
