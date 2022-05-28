import 'dart:collection';
import 'dart:developer';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:neurhome/application.dart';
import 'package:neurhome/data/db.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:wifi_info_flutter/wifi_info_flutter.dart';

import '../launcher_assist.dart';

class ApplicationsModel extends ChangeNotifier {
  final List<Application> _installedApps = [];
  final List<Application> _topApps = [];
  final List<String> _query = [];
  SharedPreferences? _prefs;
  final Map<int, Application> _favorites = {};

  final MethodChannel _platform;
  final DB _db;
  final WifiInfo _wifiInfo = WifiInfo();

  UnmodifiableListView<Application> get filtered =>
      UnmodifiableListView(_topApps);

  UnmodifiableListView<Application> get installed =>
      UnmodifiableListView(_installedApps);

  UnmodifiableMapView<int, Application> get favorites =>
      UnmodifiableMapView(_favorites);

  UnmodifiableListView<String> get hiddenPackages =>
      UnmodifiableListView(["sd"]);

  String get query => _query.map((c) => "[$c]").join();

  ApplicationsModel(this._platform, this._db);

  void initPreferences() async {
    _prefs = await SharedPreferences.getInstance();
  }

  void updateInstalled() async {
    log("updateInstalled");

    var topApps = _db.topApps();

    var installedApps = _platform.invokeListMethod('listApps');

    Map countMap = {
      for (var row in await topApps) row["package"]: row["count"]
    };

    var apps = (await installedApps ?? [])
        .map<Application>((a) => Application.fromMap(a, countMap));

    _installedApps
      ..clear()
      ..addAll(apps)
      ..sort();

    notifyListeners();
  }

  void updateTopApps() async {
    log("updateTopApps");
    var topApps = (await _db.topAppsForTimeSlot())
        .where((app) => isVisible(app["package"]))
        .toList(growable: false);

    var countMap = {for (var row in topApps) row["package"]: row["count"]};

    var apps = await _platform.invokeListMethod(
        "listTopApps", <String, dynamic>{
      'count': 6,
      'apps': topApps
    }).then((apps) => apps!
        .map((a) => Application.fromMap(a, countMap))
        .toList(growable: false));

    _topApps
      ..clear()
      ..addAll(apps);

    log(topApps.toString());
    log(apps.toString());

    notifyListeners();
  }

  void pushQuery(String query) {
    _query.add(query);
    filter();
  }

  popQuery() {
    if (_query.isNotEmpty) _query.removeLast();
    if (_query.isEmpty) {
      updateTopApps();
    } else {
      filter();
    }
  }

  clearQuery() {
    _query.clear();
    updateTopApps();
  }

  void filter() {
    var re = RegExp("\\b(my)?$query", caseSensitive: false);

    var filteredApps = _installedApps
        .where((app) => re.hasMatch(app.label))
        .where((app) => isVisible(app.package))
        .toList();

    log(filteredApps.toString());

    filteredApps.sort((a, b) => b.count.compareTo(a.count));

    _topApps
      ..clear()
      ..addAll(filteredApps.take(6));

    notifyListeners();
  }

  void launchApp(Application app) async {
    log("new launcher $app");
    LauncherAssist.launchApp(app.package);
    clearQuery();

    var wifi = await _wifiInfo.getWifiName();
    DB.instance.logAppLaunch(app, null, wifi);
  }

  Future<void> remove(String package) async {
    await _platform.invokeMethod('removeApp', <String, dynamic>{
      'package': package,
    });

    _installedApps.removeWhere((a) => a.package == package);

    notifyListeners();
  }

  void clearFavorites() async {
    assert(_prefs != null, "call initPreferences");
    await _prefs!.clear();
    updateFavorites();
  }

  void setFavorites(int index, Application application) async {
    assert(_prefs != null, "call initPreferences");
    _favorites[index] = application;

    _prefs!.setString("favorites.$index", application.package);
    updateFavorites();
  }

  bool isVisible(String package) {
    assert(_prefs != null, "call initPreferences");
    return !_prefs!.containsKey("hidden.$package");
  }

  void toggle(String package) {
    if (_prefs!.containsKey("hidden.$package")) {
      _prefs!.remove("hidden.$package");
    } else {
      _prefs!.setBool("hidden.$package", true);
    }
  }

  void updateFavorites() async {
    assert(_prefs != null, "call initPreferences");

    var favoritePackages =
        List.generate(4, (i) => _prefs!.getString("favorites.$i"));

    log(_prefs!.getKeys().toString());
    log("favorites: $favoritePackages");

    List icons = (await _platform
        .invokeListMethod("getPackagesIcons", {"packages": favoritePackages}))!;

    _favorites.clear();
    favoritePackages.asMap().entries.forEach((entry) {
      if (entry.value != null) {
        _favorites[entry.key] = Application.fromMap(
            {"package": favoritePackages[entry.key], "icon": icons[entry.key]},
            {});
      }
    });

    log("favorites");
    log(_favorites.toString());

    notifyListeners();
  }
}
