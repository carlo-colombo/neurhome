import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:launcher_assist/launcher_assist.dart';
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

const duration = const Duration(seconds: 120);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await Permission.storage.request();
  await new DB().init();

  const _platform = const MethodChannel('neurhome.carlocolombo.github.io/main');
  var applicationsModel = ApplicationsModel(_platform, new DB());
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
    child: NeurhoneApp(),
  ));
}

class NeurhoneApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Neurhome',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        textTheme: GoogleFonts.comfortaaTextTheme(
            Typography.material2018(platform: TargetPlatform.android).white),
        iconTheme: IconThemeData(color: Colors.white, size: 40),
        popupMenuTheme: PopupMenuThemeData(
            color: Colors.blueGrey, textStyle: TextStyle(color: Colors.white)),
      ),
      home: MyHomePage(),
    );
  }
}

class Foo extends StatefulWidget {
  @override
  State<StatefulWidget> createState() {
    // TODO: implement createState
    throw UnimplementedError();
  }
}

class MyHomePage extends StatelessWidget {
  final platform = const MethodChannel('neurhome.carlocolombo.github.io/main');
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
                  ? [TopApps(onTap: launchApp), Container()]
                  : [
                      ReducedAppList(launchApp,
                          reverse: applications.query.isNotEmpty),
                      Query(
                          query: applications.query,
                          onPressed: () => applications.clearQuery())
                    ],
            ),
          )),
          KeyboardContainer(),
          BottomBar(showAllApps: this.showAllApps),
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
                      padding: EdgeInsets.all(10.0),
                      child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: <Widget>[
                            IconButton(
                                onPressed: () => Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                        builder: (context) => StatsPage())),
                                icon: Icon(Icons.table_chart)),
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
          padding: EdgeInsets.all(10.0),
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

Widget appOrX(Application app) =>
    app != null ? AppIcon(app: app) : Icon(Icons.ac_unit);

class BottomBar extends StatelessWidget {
  final showAllApps;

  const BottomBar({Key key, this.showAllApps}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
        builder: (_, applications, __) =>
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              appOrX(applications.favorites[0]),
              appOrX(applications.favorites[1]),
              IconButton(
                  onPressed: () => showAllApps(context),
                  icon: Icon(Icons.apps, size: 40)),
              appOrX(applications.favorites[2]),
              appOrX(applications.favorites[3]),
            ]));
  }
}

class AppIcon extends StatelessWidget {
  const AppIcon({
    Key key,
    @required this.app,
  }) : super(key: key);

  final Application app;

  @override
  Widget build(BuildContext context) {
    return IconButton(
        onPressed: () => LauncherAssist.launchApp(app.package),
        icon: new Image.memory(app.icon));
  }
}
