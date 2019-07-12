import 'package:geolocator/geolocator.dart';
import 'package:neurhone/data/application_log.dart';
import 'package:neurhone/main.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite/sqlite_api.dart';
import 'package:path/path.dart' as p;

class DB {
  Database _database;
  static final DB _singleton = new DB._create();

  factory DB() {
    return _singleton;
  }

  DB._create();

  init() async {
    _database = await openDatabase(
      p.join(await getDatabasesPath(), 'application_log.db'),
      onCreate: (db, version) {
        return db.execute(
          "CREATE TABLE application_log("
          "  package TEXT,"
          "  label TEXT,"
          "  latitude REAL, "
          "  longitude REAL, "
          "  wifi TEXT, "
          "  timestamp TEXT"
          ")",
        );
      },
      version: 1,
    );
  }

  log(Application app, Position pos, String wifi) async {
    _database.insert("application_log",
        ApplicationLog.fromApplication(app, pos, wifi).toMap());
  }

  query() async {
    return _database.query("application_log");
  }
}
