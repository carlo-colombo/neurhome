import 'dart:typed_data';

class Application implements Comparable {
  String label, package;
  Uint8List icon;
  int count;

  Application.fromMap(Map m, Map countMap) {
    this.package = m["package"];
    this.count = countMap[this.package] ?? 0;
    this.label = m["label"].replaceFirst("Google ", "");
    this.icon = m["icon"];
  }

  @override
  int compareTo(other) {
    return this.label.toLowerCase().compareTo(other.label.toLowerCase());
  }

  @override
  String toString() {
    return "$label: $package ($count)";
  }


}
