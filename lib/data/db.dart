import 'package:geolocator/geolocator.dart';
import 'package:neurhone/data/application_log.dart';
import 'package:neurhone/main.dart';
import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';
import 'package:sqflite/sqlite_api.dart';

import '../application.dart';

class DB {
  Database _database;
  static final DB _singleton = new DB._create();

  factory DB() {
    return _singleton;
  }

  DB._create();

  init() async {
    _database = await openDatabase(
      p.join('/sdcard', 'neurhome', 'application_log.db'),
      onCreate: (db, version) {
        return db.execute(
          """CREATE TABLE application_log(
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
              )""",
        );
      },
      version: 2,
    );
  }

  close() async {
    _database.close();
  }

  log(Application app, Position pos, String wifi) async {
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
     order by count(*) desc
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
}
