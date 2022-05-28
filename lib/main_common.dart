import 'dart:async';
import 'dart:developer';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:nested/nested.dart';
import 'package:neurhome/app_config.dart';
import 'package:neurhome/application_list.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:neurhome/query.dart';
import 'package:neurhome/stats.dart';
import 'package:neurhome/watch.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import 'package:wifi_info_flutter/wifi_info_flutter.dart';

import 'bottom_bar.dart';
import 'data/db.dart';
import 'data/stats_model.dart';
import 'keyboard/keyboard.dart';

const neurhomeMainChannel = "neurhome.carlocolombo.github.io/main";

Future<List<SingleChildWidget>> initialize(AppConfig config) async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await Permission.storage.request();
  await DB().init();

  const platform = MethodChannel(neurhomeMainChannel);
  var applicationsModel = ApplicationsModel(platform, DB());
  applicationsModel
    ..initPreferences()
    ..updateInstalled()
    ..updateFavorites()
    ..updateTopApps();

  var statsModel = StatsModel()..update();

  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('google_fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['google_fonts'], license);
  });

  return [
    ChangeNotifierProvider.value(
      value: applicationsModel,
    ),
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

class Home extends StatelessWidget {
  final platform = const MethodChannel(neurhomeMainChannel);
  final WifiInfo _wifiInfo = WifiInfo();

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () => Future.value(false),
      child: baseLayout(
        [
          Watch(platform),
          Expanded(
              child: Consumer<ApplicationsModel>(
            builder: (a, applications, c) => Column(
              mainAxisAlignment: applications.query.isEmpty
                  ? MainAxisAlignment.start
                  : MainAxisAlignment.end,
              verticalDirection: VerticalDirection.down,
              children: applications.query.isEmpty
                  ? [const TopApps(), Container()]
                  : [
                      ReducedAppList(reverse: applications.query.isNotEmpty),
                      Query(
                          query: applications.query,
                          onPressed: () => applications.clearQuery())
                    ],
            ),
          )),
          const KeyboardContainer(),
          BottomBar(showAllApps: showAllApps),
        ],
      ),
    );
  }

  pickDatabase() async {
    FilePickerResult? result =
        await FilePicker.platform.pickFiles(type: FileType.any);
    if (result == null)
      return log("selected file: ${result?.files.first.path.toString()}");
    await DB.instance.reload(result.files.first.path!);
  }

  void showAllApps(context) async {
    var applicationsModel =
        Provider.of<ApplicationsModel>(context, listen: false);
    applicationsModel.updateInstalled();

    Navigator.push(
        context,
        MaterialPageRoute(
            builder: (context) => baseLayout([
                  Padding(
                      padding: const EdgeInsets.all(10.0),
                      child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: <Widget>[
                            IconButton(
                                onPressed: () => Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                        builder: (context) => StatsPage())),
                                icon: const Icon(Icons.table_chart)),
                            IconButton(
                                onPressed: pickDatabase,
                                icon: const Icon(Icons.upload_sharp))
                          ])),
                  AppList()
                ])));
  }

  Widget baseLayout(List<Widget> children) {
    return Scaffold(
      primary: true,
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(10.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            verticalDirection: VerticalDirection.down,
            children: children,
          ),
        ),
      ),
    );
  }

  timeIt(Stopwatch sw, String msg) {
    return (foo) {
      log("$msg: ${sw.elapsedMilliseconds}");
      sw.reset();
      return foo;
    };
  }
}
