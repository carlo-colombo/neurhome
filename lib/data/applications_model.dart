import 'dart:collection';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:neurhone/application.dart';
import 'package:neurhone/data/db.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ApplicationsModel extends ChangeNotifier {
  final List<Application> _installedApps = [];
  final List<Application> _topApps = [];
  final List<String> _query = [];
  final List<Application> _favorites = new List(4);

  MethodChannel _platform;
  DB _db;

  UnmodifiableListView<Application> get filtered =>
      UnmodifiableListView(_topApps);

  UnmodifiableListView<Application> get installed =>
      UnmodifiableListView(_installedApps);

  UnmodifiableListView<Application> get favorites =>
      UnmodifiableListView(_favorites);

  String get query => _query.map((i) => "[$i]").join();

  ApplicationsModel(this._platform, this._db);

  updateInstalled() async {
    print("updateInstalled");
    var topApps = await _db.topApps();

    Map countMap = Map.fromIterable(topApps,
        key: (row) => row["package"], value: (row) => row["count"]);

    var apps = await _platform.invokeListMethod('listApps').then((apps) =>
        apps.map((a) => Application.fromMap(a, countMap)).toList(growable: false));

    _installedApps
      ..clear()
      ..addAll(apps)
      ..sort();

    notifyListeners();
  }

  void updateTopApps() async {
    print("updateTopApps");
    var topApps = await _db.topApps();

    Map countMap = Map.fromIterable(topApps,
        key: (row) => row["package"], value: (row) => row["count"]);

    var apps = await _platform.invokeListMethod(
        "listTopApps", <String, dynamic>{
      'count': 6,
      'topApps': topApps
    }).then((apps) =>
        apps
            .map((a) => Application.fromMap(a, countMap))
            .toList(growable: false));

    _topApps
      ..clear()
      ..addAll(apps);

    notifyListeners();
  }

  void addToQuery(String query) {
    _query.add(query);
    filter();
  }

  void filter() {
    var re = new RegExp("\\b(my)?" + query, caseSensitive: false);

    var _filteredApps = _installedApps
        .where((app) => re.hasMatch(app.label))
        .toList();

    _filteredApps
        .sort((a,b) => a.count.compareTo(b.count));

    print(_filteredApps);

    _topApps
      ..clear()
      ..addAll(_filteredApps.take(6));

    notifyListeners();
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

  Future<void> remove(String package) async {
    await _platform.invokeMethod('removeApp', <String, dynamic>{
      'package': package,
    });

    await _installedApps.removeWhere((a) => a.package == package);
    notifyListeners();
  }

  void setFavorites(int index, Application application) async {
    _favorites[index] = application;
    SharedPreferences prefs = await SharedPreferences.getInstance();

    prefs.setString("favorites.${index}", application.package);
    notifyListeners();
  }

  void updateFavorites() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();

    var favoritePackages = await Future.wait(
        List.generate(4, (i) async => await prefs.getString("favorites.${i}")));

    var topApps = favoritePackages.map((package) =>
    ({
      "package": package,
      "count": 0
    })).toList(growable: false);

    var apps = await _platform.invokeListMethod(
        "listTopApps", <String, dynamic>{
      'count': 4,
      'topApps': topApps
    }).then((apps) =>
        apps.map((a) => Application.fromMap(a, {})).toList(growable: false));

    _favorites.setAll(0, apps);

    notifyListeners();
  }
}
