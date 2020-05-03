import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';

import 'package:connectivity/connectivity.dart';
import 'package:csv/csv.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
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
import 'key_cap.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await new PermissionHandler().requestPermissions([PermissionGroup.storage]);
  await new DB().init();

  const _platform = const MethodChannel('neurhome.carlocolombo.github.io/main');
  var applicationsModel = ApplicationsModel(_platform, new DB());
  applicationsModel
    ..updateTopApps()
    ..updateInstalled();

  return runApp(ChangeNotifierProvider.value(
    child: NeurhoneApp(),
    value: applicationsModel,
  ));
}

final initials = <String>[
  "0-9",
  "abc",
  "def",
  "ghi",
  "jkl",
  "mno",
  "pqrs",
  "tuv",
  "wxyz"
];

class NeurhoneApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Neurhome',
      theme: ThemeData(
          primarySwatch: Colors.blue,
          textTheme: Typography(platform: TargetPlatform.android).white),
      home: MyHomePage(),
    );
  }
}

class MyHomePage extends StatelessWidget {
  final platform = const MethodChannel('neurhome.carlocolombo.github.io/main');
  final PermissionHandler permissionHandler = new PermissionHandler();
  final connectivity = Connectivity();
  final geolocator = Geolocator();

  @override
  Widget build(BuildContext context) {
    var am = Provider.of<ApplicationsModel>(context, listen: false);
    return WillPopScope(
      onWillPop: () => Future.value(false),
      child: baseLayout(
        <Widget>[
          Consumer<ApplicationsModel>(
              builder: (_, applications, __) => (applications.query.isEmpty)
                  ? Watch(platform)
                  : Query(
                      query: applications.query,
                      onPressed: () => applications.clearQuery())),
          ReducedAppList(launchApp),
          Spacer(),
          Container(
              child: Wrap(
            alignment: WrapAlignment.center,
            children: <Widget>[
              ...initials.toList().map((l) {
                var letter = Text(
                  l,
                  style: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
                );
                return KeyCap(child: letter, onTap: () => am.addToQuery(l));
              }).toList(),
              KeyCap(
                border: false,
                child: Icon(Icons.backspace, color: Colors.white, size: 32),
                onTap: () => am.popQuery(),
              )
            ],
          )),
          Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
            AppIcon(app: am.filtered[0]),
            AppIcon(app: am.filtered[1]),
            IconButton(
                onPressed: () => showAllApps(context),
                icon: Icon(Icons.apps, size: 40, color: Colors.white)),
            AppIcon(app: am.filtered[2]),
            AppIcon(app: am.filtered[3]),
          ]),
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
                                onPressed: () => createFile(context),
                                icon: Icon(
                                  Icons.file_download,
                                  size: 40,
                                  color: Colors.white,
                                )),
                            IconButton(
                                onPressed: applicationsModel.updateInstalled,
                                icon: Icon(
                                  Icons.refresh,
                                  size: 40,
                                  color: Colors.white,
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
    PermissionStatus status =
        await permissionHandler.checkPermissionStatus(PermissionGroup.location);
    if (status != PermissionStatus.granted) {
      await permissionHandler.requestPermissions([PermissionGroup.location]);
    }

    print("launchApp ${app.package}");
    LauncherAssist.launchApp(app.package);

    Provider.of<ApplicationsModel>(context, listen: false).clearQuery();

    var vals = await Future.wait(<Future>[
      connectivity.getWifiName(),
      await geolocator.isLocationServiceEnabled()
          ? geolocator.getCurrentPosition(
              desiredAccuracy: LocationAccuracy.high)
          : geolocator.getLastKnownPosition(
              desiredAccuracy: LocationAccuracy.high)
    ]);

    print("pos: ${vals[1]}");

    new DB().log(app, vals[1], vals[0]);
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
        onPressed: () {
          return LauncherAssist.launchApp(app.package);
        },
        icon: new Image.memory(app.icon,
            fit: BoxFit.scaleDown, width: 40.0, height: 40.0));
  }
}
