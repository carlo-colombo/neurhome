import 'dart:io';
import 'dart:typed_data';

import 'package:connectivity/connectivity.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:launcher_assist/launcher_assist.dart';
import 'package:neurhone/application_list.dart';
import 'package:neurhone/watch.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:geolocator/geolocator.dart';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as p;
import 'package:toast/toast.dart';
import 'package:path_provider/path_provider.dart';
import 'package:csv/csv.dart';

import 'dart:convert';

import 'data/db.dart';

void main() async {
  await new DB().init();

  return runApp(NeurhoneApp());
}

class NeurhoneApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Neurhone',
      theme: ThemeData(
          // This is the theme of your application.
          //
          // Try running your application with "flutter run". You'll see the
          // application has a blue toolbar. Then, without quitting the app, try
          // changing the primarySwatch below to Colors.green and then invoke
          // "hot reload" (press "r" in the console where you ran "flutter run",
          // or simply save your changes to "hot reload" in a Flutter IDE).
          // Notice that the counter didn't reset back to zero; the application
          // is not restarted.
          primarySwatch: Colors.blue,
          textTheme: Typography(platform: TargetPlatform.android).white),
      home: MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  static const platform =
      const MethodChannel('neurhome.carlocolombo.github.io/removeApplication');

  var userWallpaper;

  List installedAppDetails = [];
  List visibleApps = [];

  PermissionHandler permissionHandler = new PermissionHandler();
  var connectivity = Connectivity();
  var geolocator = Geolocator();

  @override
  Widget build(BuildContext context) {
    return baseLayout(
      <Widget>[
        Watch(platform),
        AppList(visibleApps, launchApp, removeApplication),
        Center(
          child: IconButton(
              onPressed: showAllApps,
              icon: Icon(Icons.apps, size: 40, color: Colors.white)),
        )
      ],
    );
  }

  void showAllApps() async {
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
                                onPressed: createFile,
                                icon: Icon(
                                  Icons.file_download,
                                  size: 40,
                                  color: Colors.white,
                                )),
                            IconButton(
                                onPressed: updateAppsAndBackground,
                                icon: Icon(
                                  Icons.refresh,
                                  size: 40,
                                  color: Colors.white,
                                ))
                          ])),
                  AppList(installedAppDetails, launchApp, removeApplication)
                ])));
  }

  Future<void> removeApplication(String package) async {
    print("Trying to uninstall $package ...");
    try {
      final int result =
          await platform.invokeMethod('removeApplication', <String, dynamic>{
        'package': package,
      });
      print("Uninstalled $package ...");
      updateAppsAndBackground();
    } on Exception catch (e) {
      print("Cannot delete application $package");
    }
  }

  Widget baseLayout(List<Widget> children) {
    return Scaffold(
        primary: true,
        body: Stack(children: [
          userWallpaper == null
              ? new Center()
              : new Image.memory(userWallpaper,
                  fit: BoxFit.cover,
                  height: double.infinity,
                  width: double.infinity),
          Container(
            height: MediaQuery.of(context).size.height,
            width: MediaQuery.of(context).size.width,
            decoration: BoxDecoration(color: Colors.black.withAlpha(90)),
          ),
          SafeArea(
            child: Padding(
              padding: EdgeInsets.all(10.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.start,
                children: children,
              ),
            ),
          ),
        ]));
  }

  void updateAppsAndBackground() async {
    print("get background");
    permissionHandler
        .requestPermissions([PermissionGroup.storage])
        .then((res) => LauncherAssist.getWallpaper())
        .then((_imageData) async {
          setState(() {
            userWallpaper = _imageData;
          });
        });

    print("refreshing apps");
    LauncherAssist.getAllApps()
        .then((apps) => apps.map((a) => Application.fromMap(a)).toList())
        .then((apps) {
      apps.sort();
      return apps;
    }).then((appDetails) async {
      setState(() {
        installedAppDetails = appDetails;
        visibleApps = appDetails.sublist(0, 6);
      });
    });
  }

  void createFile() async {
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

  void launchApp(Application app) async {
    PermissionStatus status =
        await permissionHandler.checkPermissionStatus(PermissionGroup.location);
    if (status != PermissionStatus.granted) {
      await permissionHandler.requestPermissions([PermissionGroup.location]);
    }

    print("launchApp ${app.package}");
    LauncherAssist.launchApp(app.package);

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

  @override
  void initState() {
    super.initState();
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

    updateAppsAndBackground();
  }
}

class Application implements Comparable {
  String label, package;
  Uint8List icon;

  Application.fromMap(Map m) {
    this.label = m["label"].replaceFirst("Google ", "");
    this.package = m["package"];
    this.icon = m["icon"];
  }

  @override
  int compareTo(other) {
    return this.label.compareTo(other.label);
  }
}
