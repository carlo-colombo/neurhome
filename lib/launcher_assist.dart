import 'package:neurhome/platform.dart';

class LauncherAssist {
  static launchApp(String packageName) {
    NeurhomePlatform.instance
        .invokeMethod("launchApp", {"packageName": packageName});
  }

  static launchClock() {
    NeurhomePlatform.instance.invokeMethod("openClock");
  }
}
