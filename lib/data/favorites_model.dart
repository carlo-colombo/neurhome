import 'dart:collection';
import 'dart:developer';

import 'package:flutter/cupertino.dart';
import 'package:neurhome/platform.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../application.dart';

const shortcutsPrefix = "shortcuts";

class ShortcutsModel extends ChangeNotifier {
  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
  }

  UnmodifiableMapView<int, Application> get apps =>
      UnmodifiableMapView(shortcuts);

  SharedPreferences? _prefs;
  final Map<int, Application> shortcuts = {};

  void clearShortcuts() async {
    assert(_prefs != null, "call init");

    Future.wait(_prefs!
        .getKeys()
        .where((element) => element.startsWith(RegExp(shortcutsPrefix)))
        .map((element) => _prefs!.remove(element)));
    updateShortcuts();
  }

  void setShortcut(int index, Application application) async {
    assert(_prefs != null, "call init");
    shortcuts[index] = application;

    _prefs!.setString("$shortcutsPrefix.$index", application.package);
    updateShortcuts();
  }

  void updateShortcuts() async {
    assert(_prefs != null, "call init");

    var shortcutsPackages =
        List.generate(4, (i) => _prefs!.getString("$shortcutsPrefix.$i"));

    log(_prefs!.getKeys().toString());
    log("shortcuts: $shortcutsPackages");

    List icons = (await NeurhomePlatform.instance.invokeListMethod(
        "getPackagesIcons", {"packages": shortcutsPackages}))!;

    shortcuts.clear();
    shortcutsPackages.asMap().entries.forEach((entry) {
      if (entry.value != null) {
        shortcuts[entry.key] = Application.fromMap(
            {"package": shortcutsPackages[entry.key], "icon": icons[entry.key]},
            {});
      }
    });

    log("shortcuts");
    log(shortcuts.toString());

    notifyListeners();
  }
}
