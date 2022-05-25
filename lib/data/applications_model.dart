import 'dart:collection';
import 'dart:developer';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:neurhome/application.dart';
import 'package:neurhome/data/db.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:device_apps/device_apps.dart' as device;

class ApplicationsModel extends ChangeNotifier {
  final List<Application> _installedApps = [];
  final List<Application> _topApps = [];
  final List<String> _query = [];
  final Map<int, Application> _favorites = {};

  final MethodChannel _platform;
  final DB _db;

  UnmodifiableListView<Application> get filtered =>
      UnmodifiableListView(_topApps);

  UnmodifiableListView<Application> get installed =>
      UnmodifiableListView(_installedApps);

  UnmodifiableMapView<int, Application> get favorites =>
      UnmodifiableMapView(_favorites);

  String get query => _query.map((c) => "[$c]").join();

  ApplicationsModel(this._platform, this._db);

  updateInstalled() async {
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
    var topApps = await _db.topAppsForTimeSlot();

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

    var filteredApps =
        _installedApps.where((app) => re.hasMatch(app.label)).toList();

    filteredApps.sort((a, b) => b.count.compareTo(a.count));

    _topApps
      ..clear()
      ..addAll(filteredApps.take(6));

    notifyListeners();
  }

  Future<void> remove(String package) async {
    await _platform.invokeMethod('removeApp', <String, dynamic>{
      'package': package,
    });

    _installedApps.removeWhere((a) => a.package == package);
    notifyListeners();
  }

  void setFavorites(int index, Application application) async {
    _favorites[index] = application;
    SharedPreferences prefs = await SharedPreferences.getInstance();

    prefs.setString("favorites.$index", application.package);
    notifyListeners();
  }

  void updateFavorites() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();

    var favoritePackages = await Future.wait(
        List.generate(4, (i) async => prefs.getString("favorites.$i")));

    log(prefs.getKeys().toString());
    log("favorites: $favoritePackages");

    var topApps = favoritePackages
        .map((package) => ({"package": package, "count": 0}))
        .toList(growable: false);

    log(topApps.toString());

    await _platform.invokeListMethod("listTopApps",
        <String, dynamic>{'count': 4, 'topApps': topApps}).then((apps) {
      apps!.asMap().entries.map((entry) {
        _favorites[entry.key] = Application.fromMap(entry.value, {});
      });
    });

    log(_favorites.toString());

    notifyListeners();
  }
}
