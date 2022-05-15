import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:neurhome/application_list.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:neurhome/query.dart';
import 'package:neurhome/stats.dart';
import 'package:neurhome/watch.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import 'package:wifi_info_flutter/wifi_info_flutter.dart';

import 'application.dart';
import 'data/db.dart';
import 'data/stats_model.dart';
import 'keyboard.dart';
import 'launcher_assist.dart';

const duration = Duration(seconds: 120);
const NeurhomeMainChannel = "neurhome.carlocolombo.github.io/main";

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await Permission.storage.request();
  await DB().init();

  const platform = MethodChannel(NeurhomeMainChannel);
  var applicationsModel = ApplicationsModel(platform, DB());
  applicationsModel
    ..updateFavorites()
    ..updateTopApps()
    ..updateInstalled();

  var statsModel = StatsModel()..update();

  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('google_fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['google_fonts'], license);
  });

  return runApp(MultiProvider(
    providers: [
      ChangeNotifierProvider.value(
        value: applicationsModel,
      ),
      ChangeNotifierProvider.value(value: statsModel)
    ],
    child: NeurhomeApp(),
  ));
}

class NeurhomeApp extends StatelessWidget {
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
      home: MyHomePage(),
    );
  }
}

class MyHomePage extends StatelessWidget {
  final platform = const MethodChannel(NeurhomeMainChannel);
  final WifiInfo _wifiInfo = WifiInfo();

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () => Future.value(false),
      child: baseLayout(
        [
          Watch(platform),
          // Expanded(
          //     child: Consumer<ApplicationsModel>(
          //   builder: (a, applications, c) => Column(
          //     mainAxisAlignment: applications.query.isEmpty
          //         ? MainAxisAlignment.start
          //         : MainAxisAlignment.end,
          //     verticalDirection: VerticalDirection.down,
          //     children: applications.query.isEmpty
          //         ? [TopApps(onTap: launchApp), Container()]
          //         : [
          //             ReducedAppList(launchApp,
          //                 reverse: applications.query.isNotEmpty),
          //             Query(
          //                 query: applications.query,
          //                 onPressed: () => applications.clearQuery())
          //           ],
          //   ),
          // )),
          const KeyboardContainer(),
          BottomBar(showAllApps: showAllApps),
        ],
      ),
    );
  }

  Future<bool> _onBackPressed() async {
    return false;
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
                          ])),
                  AppList(
                    launchApp,
                  )
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

  getAllApps() {
    return platform.invokeMethod('listApps');
  }

  timeIt(Stopwatch sw, String msg) {
    return (foo) {
      print("${msg}: ${sw.elapsedMilliseconds}");
      sw.reset();
      return foo;
    };
  }

  void launchApp(context, Application app) async {
    try {
      LauncherAssist.launchApp(app.package);

      Provider.of<ApplicationsModel>(context, listen: false).clearQuery();

      var wifi = await _wifiInfo.getWifiName();
      print(wifi);

      await DB.instance.log(app, null, wifi);
      print("logged");
    } catch (e) {
      print("ERROR: ${e}");
    }
  }
}

Widget appOrX(ApplicationsModel apps, int index) =>
    apps.favorites.containsKey(index)
        ? AppIcon(app: apps.favorites[index]!)
        : const Icon(Icons.ac_unit);

class BottomBar extends StatelessWidget {
  final showAllApps;

  const BottomBar({Key? key, this.showAllApps}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
        builder: (_, applications, __) =>
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              appOrX(applications, 0),
              appOrX(applications, 1),
              IconButton(
                  onPressed: () => showAllApps(context),
                  icon: const Icon(Icons.apps, size: 40)),
              appOrX(applications, 2),
              appOrX(applications, 3),
            ]));
  }
}

class AppIcon extends StatelessWidget {
  const AppIcon({
    Key? key,
    required this.app,
  }) : super(key: key);

  final Application app;

  @override
  Widget build(BuildContext context) {
    return IconButton(
        onPressed: () => LauncherAssist.launchApp(app.package),
        icon: Image.memory(app.icon));
  }
}
