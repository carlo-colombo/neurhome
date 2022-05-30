import 'package:flutter/services.dart';

class NeurhomePlatform {
  static const MethodChannel _platform = MethodChannel(_neurhomeMainChannel);
  static const _neurhomeMainChannel = "neurhome.carlocolombo.github.io/main";

  static MethodChannel get instance => _platform;
}
