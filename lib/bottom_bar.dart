import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:provider/provider.dart';

import 'application.dart';
import 'launcher_assist.dart';

Widget FavoriteApp(ApplicationsModel apps, int index) =>
    apps.favorites.containsKey(index)
        ? AppIcon(app: apps.favorites[index]!)
        : const Icon(Icons.settings_overscan);

class BottomBar extends StatelessWidget {
  final showAllApps;

  const BottomBar({Key? key, this.showAllApps}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
        builder: (_, applications, __) =>
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              FavoriteApp(applications, 0),
              FavoriteApp(applications, 1),
              IconButton(
                  onPressed: () => showAllApps(context),
                  icon: const Icon(Icons.apps, size: 40)),
              FavoriteApp(applications, 2),
              FavoriteApp(applications, 3),
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
