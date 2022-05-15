import 'package:geolocator/geolocator.dart';
import 'package:neurhome/data/application_log.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'package:sqflite/sqlite_api.dart';

import '../application.dart';

class DB {
  late Database _database;
  static final DB _singleton = DB._create();

  static DB get instance => _singleton;

  factory DB() {
    return _singleton;
  }

  DB._create();

  _createApplicationLog(Batch batch) {
    batch.execute("""
      CREATE TABLE application_log(
        package TEXT,
        label TEXT,
        latitude REAL,
        longitude REAL,
        wifi TEXT,
        timestamp TEXT,
        geohash TEXT,
        geohash_7 TEXT,
        geohash_9 TEXT,
        x REAL,
        y REAL,
        z REAL
      )
    """);
  }

  _createView_packages_time_difference(Batch batch) {
    batch.execute("""
        create view if not exists packages_time_difference as select
          package,
          (
            strftime('%s', time(timestamp)) - strftime('%s', '2000-01-01T00:00:00.0')
          ) / 60 as t,
          (
            strftime(
              '%s',
              time('now', 'localtime')
            ) - strftime('%s', '2000-01-01T00:00:00.0')
          ) / 60 as now
        from
          application_log
        where
          timestamp > date('now', '-3 months')
        order by
          package;
    """);
  }

  init() async {
    _database =
        await openDatabase(p.join('sdcard', 'neurhome', 'application_log.db'),
            onCreate: (db, version) async {
              var batch = db.batch();
              _createApplicationLog(batch);
              _createView_packages_time_difference(batch);
              await batch.commit();
              return;
            },
            version: 3,
            onUpgrade: (db, oldV, newV) async {
              var batch = db.batch();
              if (oldV < 3) {
                _createView_packages_time_difference(batch);
              }
              await batch.commit();
              return;
            });
  }

  close() async {
    _database.close();
  }

  log(Application app, Position? pos, String? wifi) async {
    print("log ${app.package}");
    await _database.insert("application_log",
        ApplicationLog.fromApplication(app, pos, wifi).toMap());
  }

  _normalizeField(String field) {
    return """
      case when $field is null then 0 else ( 
        SELECT COUNT(*) + 1 
          FROM "application_log" 
          WHERE $field < a.$field
        ) end as ${field}ID
    """;
  }

  Future<List<Map<String, dynamic>>> topApps() async {
    return _database.rawQuery("""
     select package, count(*) as count
     from application_log
     group by package
     having count > 0
     order by count(*) desc
    """);
  }

  Future<List<Map<String, dynamic>>> topAppsForTimeSlot() async {
    return _database.rawQuery("""
      select
        package,
        count(*) as count
      from
        packages_time_difference
      where
        min(abs(t - now), (24 * 60) - abs(t - now)) < 20
      group by
        package
      order by
        count(*) desc
    """);
  }

  query() async {
    var normalized = ["package", "wifi", "geohash", "geohash_7", "geohash_9"]
        .map(_normalizeField)
        .join(",");

    return (await _database.rawQuery("""
      select a.*,
      strftime('%H',timestamp) as hours,
        strftime('%M',timestamp) as minutes,
        strftime('%H',timestamp) * 60 +
        strftime('%M',timestamp) as minutes2,
        strftime('%w',timestamp) as day_week,
        package,
        $normalized
        from application_log a
        
    """));
  }

  Future<List<Map<String, dynamic>>> rawQuery(String sql) async =>
      _database.rawQuery(sql);
}
