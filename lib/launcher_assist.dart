import 'package:flutter/services.dart';

class LauncherAssist {
  static const MethodChannel _channel =
      MethodChannel("neurhome.carlocolombo.github.io/main");

  /// Returns a list of apps installed on the user's device
  static getAllApps() async {
    var data = await _channel.invokeMethod('getAllApps');
    return data;
  }

  /// Launches an app using its package name
  static launchApp(String packageName) {
    _channel.invokeMethod("launchApp", {"packageName": packageName});
  }
}
