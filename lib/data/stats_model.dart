import 'dart:collection';
import 'dart:developer';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:neurhome/data/db.dart';

class StatsModel extends ChangeNotifier {
  final List<Map<String, dynamic>> _topApps = [];
  final List<Map<String, dynamic>> _lastOpened = [];

  final DB _db = DB.instance;

  UnmodifiableListView<Map<String, dynamic>> get topApps =>
      UnmodifiableListView(_topApps);

  UnmodifiableListView<Map<String, dynamic>> get lastOpened =>
    UnmodifiableListView(_lastOpened);

  void update() async {
    log("update stats");

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
