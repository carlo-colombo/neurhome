import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';
import 'dart:ui';

import 'package:connectivity/connectivity.dart';
import 'package:csv/csv.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:launcher_assist/launcher_assist.dart';
import 'package:neurhone/application_list.dart';
import 'package:neurhone/watch.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:toast/toast.dart';

import 'data/db.dart';
import 'draw/draw.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);

  await new PermissionHandler().requestPermissions([PermissionGroup.storage]);
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

  List installedAppDetails = [];
  List<String> initials = [];
  List visibleApps = [];
  List<DrawingPoints> points = [];
  String query = "";

  Canvas generateImageCanvas;
  PictureRecorder recorder;

  PermissionHandler permissionHandler = new PermissionHandler();
  var connectivity = Connectivity();
  var geolocator = Geolocator();
  Timer cancellationTimer;

  @override
  Widget build(BuildContext context) {
    List visibleApps;
    if (query != "") {
      var re = new RegExp("\\b" + query, caseSensitive: false);
      var filteredApps =
          installedAppDetails.where((ai) => re.hasMatch(ai.label)).toList();
      visibleApps = filteredApps.sublist(0, min(6, filteredApps.length));
      initials = getInitials(filteredApps, query);
    } else if (installedAppDetails.isNotEmpty) {
      visibleApps = installedAppDetails.sublist(0, 6);
      initials = getInitials(installedAppDetails, query);
    } else {
      visibleApps = installedAppDetails;
      initials = getInitials(installedAppDetails, query);
    }

    var onPressed2 = () {
      setState(() {
        query = "";
        initials = getInitials(installedAppDetails, query);
      });
    };
    var willPopScope = WillPopScope(
        onWillPop: _onBackPressed,
        child: Stack(children: [
          CustomPaint(
            size: Size.infinite,
            painter: DrawingPainter(
                pointsList: points, exportImageCanvas: generateImageCanvas),
          ),
          baseLayout(
            <Widget>[
              query?.isEmpty
                  ? Watch(platform)
                  : Query(query: query, onPressed: onPressed2),
              ReducedAppList(visibleApps, launchApp),
              Spacer(),
              Container(
                  child: Wrap(
                alignment: WrapAlignment.center,
                children: <Widget>[
                  ...initials.toList().map((l) {
                    var addToQuery = () {
                      setState(() => query += l);
                    };
                    var letter = Text(
                      l,
                      style:
                          TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
                    );
                    return KeyCap(child: letter, onTap: addToQuery);
                  }).toList(),
                  KeyCap(
                    child: Icon(Icons.backspace, color: Colors.white, size: 32),
                    onTap: () => setState(() {
                      query = query.substring(0, query.length - 1);
                    }),
                  )
                ],
              )),
              Center(
                child: IconButton(
                    onPressed: showAllApps,
                    icon: Icon(Icons.apps, size: 40, color: Colors.white)),
              )
            ],
          ),
        ]));

    return willPopScope;
  }

  RegExp _alphanumeric = RegExp(r'[a-zA-Z0-9]');

  bool isAlphanumeric(String str) {
    return _alphanumeric.hasMatch(str);
  }

  DrawingPoints buildDrawingPoints(BuildContext context, dynamic details) {
    RenderBox renderBox = context.findRenderObject();
    return DrawingPoints(
      points: renderBox.globalToLocal(details.globalPosition),
    );
  }

  Future<bool> _onBackPressed() async {
    return false;
  }

  void showAllApps() async {
    updateApps();

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
                                onPressed: updateApps,
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
    Stopwatch sw = Stopwatch();
    try {
      final int result =
          await platform.invokeMethod('removeApplication', <String, dynamic>{
        'package': package,
      });
      print("Uninstalled $package ...");
    } on Exception catch (e) {
      print("Cannot delete application $package");
    }
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

  void updateApps() async {
    print("refreshing apps");
    var sw = Stopwatch();

    sw.start();
    Future appsFuture = getAllApps()
        .then(timeIt(sw, "getting apps"))
        .then((apps) =>
            apps.map((a) => Application.fromMap(a)).toList(growable: false))
        .then(timeIt(sw, "converting to list"))
        .then((appDetails) async {
      appDetails.sort();
      installedAppDetails = appDetails;
      initials = getInitials(installedAppDetails, query);
      print(initials);
    }).then(timeIt(sw, "sorting apps"));

    Stopwatch sw2 = Stopwatch();
    (installedAppDetails.isEmpty ? appsFuture : Future.value(null))
        .then(timeIt(sw2, "waiting if empty"))
        .then((_) => setState(() {}));
  }

  List getInitials(applications, query) {
    Set<String> initials = Set();
    int index = query.length;
    RegExp q = new RegExp("^" + query,caseSensitive: false);

    applications.forEach((app) {
      initials.addAll((app as Application)
          .label
          .split(" ")
          .map((w) =>
              index < w.length && q.hasMatch(w) ? w[index].toLowerCase() : "")
          .where((l) => l.isNotEmpty && isAlphanumeric(l)));
    });
    return initials.toList()..sort();
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

    recorder = new PictureRecorder();
    generateImageCanvas = new Canvas(recorder);

    updateApps();
  }
}

class KeyCap extends StatelessWidget {
  const KeyCap({
    Key key,
    @required this.child,
    @required this.onTap,
  }) : super(key: key);

  final Widget child;
  final Null Function() onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      child: GestureDetector(
        child: child,
        onTap: onTap,
      ),
      padding: EdgeInsets.all(6),
      margin: EdgeInsets.all(6),
      decoration: BoxDecoration(border: Border.all(color: Colors.white)),
    );
  }
}

class Query extends StatelessWidget {
  const Query({
    Key key,
    @required this.query,
    @required this.onPressed,
  }) : super(key: key);

  final String query;
  final void Function() onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      child: Row(
        children: [
          Text(
            query,
            style: Theme.of(context).textTheme.title,
            overflow: TextOverflow.ellipsis,
            textScaleFactor: 2,
          ),
          Spacer(),
          IconButton(
              onPressed: onPressed,
              icon: Icon(Icons.cancel, size: 40, color: Colors.white)),
        ],
      ),
      padding: EdgeInsets.all(10),
    );
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
