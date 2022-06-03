import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:nested/nested.dart';
import 'package:neurhome/app_config.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:neurhome/pages/home.dart';
import 'package:neurhome/platform.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';

import 'data/db.dart';
import 'data/favorites_model.dart';
import 'data/stats_model.dart';

Future<List<SingleChildWidget>> initialize(AppConfig config) async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await Permission.storage.request();
  await DB().init();

  var shortcutsModel = ShortcutsModel();
  var applicationsModel = ApplicationsModel(NeurhomePlatform.instance, DB());

  await shortcutsModel.init().then((_) => applicationsModel.init());

  await shortcutsModel.updateShortcuts();

  await applicationsModel.updateTopApps();

  applicationsModel.updateInstalled();

  var statsModel = StatsModel()..update();

  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('google_fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['google_fonts'], license);
  });

  return [
    ChangeNotifierProvider.value(value: applicationsModel),
    ChangeNotifierProvider.value(value: shortcutsModel),
    ChangeNotifierProvider.value(value: statsModel),
    Provider(create: (_) => config)
  ];
}

void instantiateAndRunApp(List<SingleChildWidget> providers) async {
  return runApp(MultiProvider(
    providers: providers,
    child: const NeurhomeApp(),
  ));
}

class NeurhomeApp extends StatelessWidget {
  const NeurhomeApp({Key? key}) : super(key: key);

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Neurhome',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        textTheme: GoogleFonts.comfortaaTextTheme(
            Typography.material2018(platform: TargetPlatform.android).white),
        iconTheme: const IconThemeData(color: Colors.white, size: 40),
        popupMenuTheme: const PopupMenuThemeData(
            color: Colors.blueGrey, textStyle: TextStyle(color: Colors.white)),
      ),
      home: Home(),
    );
  }
}
