import 'dart:collection';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:neurhome/application.dart';
import 'package:neurhome/data/db.dart';
import 'package:shared_preferences/shared_preferences.dart';

class StatsModel extends ChangeNotifier {
  final List<Map<String, dynamic>> _topApps = [];
  final List<Map<String, dynamic>> _lastOpened = [];

  MethodChannel _platform;
  DB _db;

  UnmodifiableListView<Map<String, dynamic>> get topApps =>
      UnmodifiableListView(_topApps);

  UnmodifiableListView<Map<String, dynamic>> get lastOpened =>
    UnmodifiableListView(_lastOpened);

  StatsModel(){
    _db = DB.instance;
  }

  void update() async {
    print("update stats");

    var stats = await Future.wait([
      _db.topAppsForTimeSlot(),
      _db.rawQuery("""
        select package, timestamp
        from application_log
        order by timestamp desc
        limit 20
      """)
    ]);

    _topApps
      ..clear()
      ..addAll(stats[0]);

    _lastOpened
      ..clear()
      ..addAll(stats[1]);

    notifyListeners();
  }
}
