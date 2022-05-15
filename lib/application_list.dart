import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:provider/provider.dart';

class AppList extends StatelessWidget {
  final Function onTap;

  AppList(this.onTap);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
      builder: (context, applications, child) => Flexible(
          child: ListView.builder(
        itemBuilder: (context, i) {
          var appDetail = applications.installed[i];

          return AppItem(
            onTap: () => onTap(context, appDetail),
            onLongPress: () {
              showMenu(
                  context: context,
                  position: const RelativeRect.fromLTRB(0, 30, 0, 0),
                  items: <PopupMenuEntry>[
                    PopupMenuItem(
                        child: Text(
                      appDetail.label,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    )),
                    const PopupMenuDivider(),
                    ...List.generate(
                        4,
                        (index) => PopupMenuItem(
                              value: () =>
                                  applications.setFavorites(index, appDetail),
                              child: Text("Favorite #${index + 1}"),
                            )),
                    const PopupMenuDivider(),
                    PopupMenuItem(
                        value: () => applications.remove(appDetail.package),
                        child: const Text('Remove'))
                  ]).then((fn) => fn?.call());
            },
            appDetail: appDetail,
          );
        },
        itemCount: applications.installed.length,
      )),
    );
  }
}

class ReducedAppList extends StatelessWidget {
  final Function onTap;
  final bool reverse;

  ReducedAppList(this.onTap, {required this.reverse});

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
      builder: (context, applications, child) {
        var _apps =
            reverse ? applications.filtered.reversed : applications.filtered;

        return Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: _apps
              .map((ad) => AppItem(
                    onTap: () => onTap(context, ad),
                    appDetail: ad,
                    onLongPress: () {},
                  ))
              .toList(),
        );
      },
    );
  }
}

class _TopApps extends State<TopApps> {
  Timer? _timer;
  final Function onTap;

  _TopApps(this.onTap);

  @override
  Widget build(BuildContext context) {
    var applicationsModel =
        Provider.of<ApplicationsModel>(context, listen: false);

    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 100), (timer) {
      applicationsModel.updateTopApps();
    });

    return ReducedAppList(
      onTap,
      reverse: false,
    );
  }

  @override
  void dispose() {
    super.dispose();
    _timer?.cancel();
  }
}

class TopApps extends StatefulWidget {
  final Function onTap;

  const TopApps({key, required this.onTap}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return _TopApps(onTap);
  }
}

class AppItem extends StatelessWidget {
  const AppItem({
    key,
    required this.appDetail,
    required this.onTap,
    required this.onLongPress,
  }) : super(key: key);

  final appDetail;
  final onTap;
  final onLongPress;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
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
              Image.memory(appDetail.icon,
                  fit: BoxFit.scaleDown, width: 48.0, height: 48.0)
            ]),
      ),
    );
  }
}
