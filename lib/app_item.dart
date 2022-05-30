import 'package:flutter/material.dart';
import 'package:neurhome/application.dart';
import 'package:provider/provider.dart';

import 'data/applications_model.dart';
import 'data/favorites_model.dart';

void showMenuForApp(
    Application appDetail, bool isVisible, BuildContext context) {
  showMenu(
      context: context,
      position: const RelativeRect.fromLTRB(0, 30, 0, 0),
      items: <PopupMenuEntry>[
        PopupMenuItem(
            child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: <Widget>[
              Image.memory(appDetail.icon,
                  fit: BoxFit.scaleDown, width: 24.0, height: 24.0),
              Padding(
                padding: const EdgeInsets.all(10),
                child: Text(
                  appDetail.label,
                  style: Theme.of(context).textTheme.headline6,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ])),
        const PopupMenuDivider(),
        ...List.generate(
            4,
            (index) => PopupMenuItem(
                  value: () => context
                      .read<ShortcutsModel>()
                      .setShortcut(index, appDetail),
                  child: Row(
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: ShortcutIcon(index: index),
                      ),
                      Text("Favorite #${index + 1}"),
                    ],
                  ),
                )),
        const PopupMenuDivider(),
        PopupMenuItem(
            value: () => context.read<ShortcutsModel>().clearShortcuts(),
            child: const Text('Clear favorites')),
        const PopupMenuDivider(),
        CheckedPopupMenuItem(
            checked: isVisible,
            child: const Text("Visible"),
            value: () =>
                context.read<ApplicationsModel>().toggle(appDetail.package)),
        const PopupMenuDivider(),
        PopupMenuItem(
            value: () =>
                context.read<ApplicationsModel>().removeApp(appDetail.package),
            child: const Text('Remove all preferences'))
      ]).then((fn) => fn?.call());
}

Widget shortcutIcon(BuildContext context, int index) {
  return context.read<ShortcutsModel>().apps[index] != null
      ? Image.memory(context.read<ShortcutsModel>().apps[index]!.icon,
          fit: BoxFit.scaleDown, width: 24.0, height: 24.0)
      : const Icon(Icons.check_box_outline_blank, size: 24);
}

class ShortcutIcon extends StatelessWidget {
  final int index;

  const ShortcutIcon({Key? key, required this.index}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return context.read<ShortcutsModel>().apps[index] != null
        ? Image.memory(context.read<ShortcutsModel>().apps[index]!.icon,
            fit: BoxFit.scaleDown, width: 24.0, height: 24.0)
        : const Icon(Icons.check_box_outline_blank, size: 24);
  }
}

class AppItem extends StatelessWidget {
  const AppItem({
    key,
    required this.appDetail,
  }) : super(key: key);

  final Application appDetail;

  @override
  Widget build(BuildContext context) {
    var applications = context.read<ApplicationsModel>();
    var isVisible = context.select<ApplicationsModel, bool>(
        (am) => am.isVisible(appDetail.package));

    return GestureDetector(
      onTap: () async => applications.launchApp(appDetail),
      onLongPress: () => showMenuForApp(appDetail, isVisible, context),
      child: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[
              Row(
                children: [
                  Padding(
                    padding: const EdgeInsets.all(10),
                    child: Text(
                      appDetail.label,
                      style: Theme.of(context).textTheme.headline6,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (!isVisible) const Icon(Icons.visibility_off, size: 24),
                ],
              ),
              Image.memory(appDetail.icon,
                  fit: BoxFit.scaleDown, width: 48.0, height: 48.0)
            ]),
      ),
    );
  }
}
