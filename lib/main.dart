import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';

import 'package:wifi_info_flutter/wifi_info_flutter.dart';
import 'package:csv/csv.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:launcher_assist/launcher_assist.dart';
import 'package:neurhone/application_list.dart';
import 'package:neurhone/data/applications_model.dart';
import 'package:neurhone/query.dart';
import 'package:neurhone/watch.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:provider/provider.dart';
import 'package:toast/toast.dart';

import 'application.dart';
import 'data/db.dart';
import 'keyboard.dart';

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

  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('google_fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['google_fonts'], license);
  });

  return runApp(ChangeNotifierProvider.value(
    child: NeurhoneApp(),
    value: applicationsModel,
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

class MyHomePage extends StatelessWidget {
  final platform = const MethodChannel('neurhome.carlocolombo.github.io/main');
  final WifiInfo _wifiInfo = WifiInfo();

  @override
  Widget build(BuildContext context) {
    var am = Provider.of<ApplicationsModel>(context, listen: false);
    return WillPopScope(
        onWillPop: () => Future.value(false),
        child: Consumer<ApplicationsModel>(
          builder: (a, applications, c) => baseLayout(
            <Widget>[
              Watch(platform),
              Expanded(
                child: Column(
                  mainAxisAlignment: applications.query.isEmpty
                      ? MainAxisAlignment.start
                      : MainAxisAlignment.end,
                  verticalDirection: VerticalDirection.down,
                  children: <Widget>[
                    ReducedAppList(launchApp, reverse: applications.query.isNotEmpty),
                    applications.query.isNotEmpty
                        ? Query(
                            query: applications.query,
                            onPressed: () => applications.clearQuery())
                        : Container(),
                  ],
                ),
              ),
              Keyboard(applicationModel: am),
              BottomBar(showAllApps: this.showAllApps),
            ],
          ),
        ));
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
                                onPressed: () => createFile(context),
                                icon: Icon(
                                  Icons.file_download,
                                )),
                            IconButton(
                                onPressed: applicationsModel.updateInstalled,
                                icon: Icon(
                                  Icons.refresh,
                                ))
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

  void createFile(context) async {
    var entries = await new DB().query();
    var json = jsonEncode(entries);

    print(entries.length);

    new File(p.join((await getExternalStorageDirectory()).path,
            "db${DateTime.now().toIso8601String()}.csv"))
        .create(recursive: true)
        .then((File file) {
      List csvRows = [
        entries[0].keys.toList(),
        ...entries.map((Map row) => row.values.toList()).toList()
      ];

      String csv = const ListToCsvConverter()
          .convert(List.castFrom<dynamic, List>(csvRows));

      file.writeAsString(csv);

      Toast.show("File created ${file.path}", context,
          duration: Toast.LENGTH_SHORT, gravity: Toast.BOTTOM);
      print(file.path);
    });
  }

  void launchApp(context, Application app) async {
    await Permission.location.request();

    print("launchApp ${app.package}");
    LauncherAssist.launchApp(app.package);

    Provider.of<ApplicationsModel>(context, listen: false).clearQuery();

    var vals = await Future.wait(<Future>[
      _wifiInfo.getWifiName(),
      await Geolocator.isLocationServiceEnabled()
          ? Geolocator.getCurrentPosition(
              desiredAccuracy: LocationAccuracy.high)
          : Geolocator.getLastKnownPosition()
    ]);

    print("pos: ${vals[1]}");

    new DB().log(app, vals[1], vals[0]);
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
