import 'dart:math';

import 'package:geohash/geohash.dart';
import 'package:geolocator/geolocator.dart';
import 'package:neurhone/main.dart';

class ApplicationLog {
  double latitude, longitude;
  String package, label, wifi;
  DateTime timestamp;

  ApplicationLog(
      {this.latitude,
      this.longitude,
      this.package,
      this.label,
      this.wifi,
      this.timestamp});

  ApplicationLog.fromApplication(
      Application application, Position pos, String wifi) {
    this.package = application.package;
    this.label = application.label;
    this.wifi = wifi;
    if (pos != null) {
      this.latitude = pos.latitude;
      this.longitude = pos.longitude;
    }
    this.timestamp = DateTime.now();
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> json = {
      'package': package,
      'label': label,
      'wifi': wifi,
      'timestamp': timestamp.toIso8601String()
    };

    if (latitude != null) {
      var gh = Geohash.encode(latitude, longitude);

      json.addAll({
        'latitude': latitude,
        'longitude': longitude,
        'geohash': gh,
        'geohash_7': gh.substring(0, 7),
        'geohash_9': gh.substring(0, 9),
        'x': cos(latitude) * cos(longitude),
        'y': cos(latitude) * sin(longitude),
        'z': sin(latitude)
      });
    }

    return json;
  }
}
