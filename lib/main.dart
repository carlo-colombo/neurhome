import 'package:provider/provider.dart';

import 'app_config.dart';
import 'main_common.dart';

void main() async {
  var config = AppConfig(profile: "");
  instantiateAndRunApp(await initialize(config));
}
