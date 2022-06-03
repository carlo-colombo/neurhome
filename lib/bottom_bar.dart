import 'dart:developer';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:neurhome/data/favorites_model.dart';
import 'package:neurhome/pages/all_applications.dart';
import 'package:provider/provider.dart';

import 'application.dart';
import 'launcher_assist.dart';

class FavoriteApp extends StatelessWidget {
  final Application? app;

  const FavoriteApp({Key? key, this.app}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return app != null
        ? AppIcon(app: app!)
        : const Icon(Icons.settings_overscan);
  }
}

class BottomBar extends StatelessWidget {
  const BottomBar({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<ShortcutsModel>(builder: (_, shortcutsModel, __) {
      var apps = shortcutsModel.apps;
      return Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        FavoriteApp(app: apps[0]),
        FavoriteApp(app: apps[1]),
        IconButton(
            onPressed: () => showAllApps(context),
            icon: const Icon(Icons.apps, size: 40)),
        FavoriteApp(app: apps[2]),
        FavoriteApp(app: apps[3]),
      ]);
    });
  }

  void showAllApps(context) async {
    var applicationsModel =
        Provider.of<ApplicationsModel>(context, listen: false);
    await applicationsModel.updateInstalled();

    Navigator.push(context,
        MaterialPageRoute(builder: (context) => const AllApplications()));
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
