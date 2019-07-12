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

  ApplicationLog.fromApplication(Application application, Position pos, String wifi) {
    this.package = application.package;
    this.label = application.label;
    this.wifi = wifi;
    this.latitude = pos.latitude;
    this.longitude = pos.longitude;
    this.timestamp = DateTime.now();
  }

  Map<String, dynamic> toMap() {
    return {
      'latitude': latitude,
      'longitude': longitude,
      'package': package,
      'label': label,
      'wifi': wifi,
      'timestamp': timestamp.toIso8601String()
    };
  }
}
