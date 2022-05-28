import 'dart:math';

import 'package:dart_geohash/dart_geohash.dart';
import 'package:geolocator/geolocator.dart';
import 'package:neurhome/main_common.dart';

import '../application.dart';

class ApplicationLog {
  late double? latitude, longitude;
  late String package, label;
  late String? wifi;
  late DateTime timestamp;
  final GeoHasher geoHasher = GeoHasher();

  ApplicationLog.fromApplication(
      Application application, Position? pos, this.wifi) {
    package = application.package;
    label = application.label;

    latitude = pos?.latitude;
    longitude = pos?.longitude;

    timestamp = DateTime.now();
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> json = {
      'package': package,
      'label': label,
      'wifi': wifi,
      'timestamp': timestamp.toIso8601String()
    };
    final latitude = this.latitude;
    final longitude = this.longitude;

    if (latitude != null && longitude != null) {
      var gh = geoHasher.encode(latitude, longitude);

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
