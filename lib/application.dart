import 'dart:typed_data';

class Application implements Comparable {
  late String label, package;
  late Uint8List icon;
  late int count;

  Application.fromMap(Map m, Map countMap) {
    package = m["package"];
    count = countMap[package] ?? 0;
    label = m["label"].replaceFirst("Google ", "");
    icon = m["icon"];
  }

  @override
  int compareTo(other) {
    return label.toLowerCase().compareTo(other.label.toLowerCase());
  }

  @override
  String toString() {
    return "$label: $package ($count)";
  }
}
