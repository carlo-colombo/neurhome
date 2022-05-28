import 'package:flutter/material.dart';
import 'package:neurhome/application.dart';
import 'package:provider/provider.dart';

import 'data/applications_model.dart';

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
                      .read<ApplicationsModel>()
                      .setFavorites(index, appDetail),
                  child: Text("Favorite #${index + 1}"),
                )),
        const PopupMenuDivider(),
        PopupMenuItem(
            value: () => context.read<ApplicationsModel>().clearFavorites(),
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
                context.read<ApplicationsModel>().remove(appDetail.package),
            child: const Text('Remove all preferences'))
      ]).then((fn) => fn?.call());
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
      onTap: () async {
        applications.launchApp(appDetail);
      },
      onLongPress: () => showMenuForApp(appDetail, isVisible, context),
      child: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.all(10),
                child: Text(
                  appDetail.label,
                  style: Theme.of(context).textTheme.headline6,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if(!isVisible) Icon(Icons.visibility_off),
              Image.memory(appDetail.icon,
                  fit: BoxFit.scaleDown, width: 48.0, height: 48.0)
            ]),
      ),
    );
  }
}
