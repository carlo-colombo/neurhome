import 'dart:typed_data';

class Application implements Comparable {
  String label, package;
  Uint8List icon;
  int count;

  Application.fromMap(Map m, Map countMap) {
    this.label = m["label"].replaceFirst("Google ", "");
    this.package = m["package"];
    this.icon = m["icon"];
    this.count = countMap[this.package] ?? 0;
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
