import 'dart:typed_data';

import 'package:connectivity/connectivity.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:launcher_assist/launcher_assist.dart';
import 'package:neurhone/application_list.dart';
import 'package:neurhone/watch.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:geolocator/geolocator.dart';

void main() => runApp(NeurhoneApp());

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
        Watch(),
        AppList(visibleApps, launchApp),
        IconButton(
            onPressed: showAllApps,
            icon: Icon(Icons.apps, size: 40, color: Colors.white)),
      ],
    );
  }

  _permissions() async {
    PermissionStatus permission = await PermissionHandler()
        .checkPermissionStatus(PermissionGroup.storage);
  }

  void showAllApps() {
    Navigator.push(
        context,
        MaterialPageRoute(
            builder: (context) =>
                baseLayout([AppList(installedAppDetails, launchApp)])));
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

  void launchApp(String packageName) async {
    [PermissionGroup.storage, PermissionGroup.location].forEach((p) async {
      PermissionStatus status =
          await permissionHandler.checkPermissionStatus(p);
      if (status != PermissionStatus.granted) {
        await permissionHandler.requestPermissions([p]);
      }
    });

    LauncherAssist.launchApp(packageName);

    var vals = await Future.wait(<Future>[
      connectivity.getWifiName(),
      geolocator.getLastKnownPosition(desiredAccuracy: LocationAccuracy.high)
    ]);

    print(
        "$packageName ${new DateTime.now()} ${vals[0]} ${vals[1]} ${vals[1].accuracy}");
  }

  @override
  void initState() {
    super.initState();
    SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

    permissionHandler
        .requestPermissions([PermissionGroup.storage])
        .then((res) => LauncherAssist.getWallpaper())
        .then((_imageData) async {
          setState(() {
            userWallpaper = _imageData;
          });
        });

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
}

class Application implements Comparable {
  String label, package;
  Uint8List icon;

  Application.fromMap(Map m) {
    this.label = m["label"];
    this.package = m["package"];
    this.icon = m["icon"];
  }

  @override
  int compareTo(other) {
    return this.label.compareTo(other.label);
  }
}
