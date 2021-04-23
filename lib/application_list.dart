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

          return new AppItem(
            onTap: () => onTap(context, appDetail),
            onLongPress: () {
              showMenu(
                  context: context,
                  position: RelativeRect.fromLTRB(0, 30, 0, 0),
                  items: <PopupMenuEntry>[
                    PopupMenuItem(
                        child: Text(
                      appDetail.label,
                      style: TextStyle(fontWeight: FontWeight.bold),
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
                        child: Text('Remove'))
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

  ReducedAppList(this.onTap, {this.reverse});

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
  Timer _timer;
  final Function onTap;

  _TopApps(this.onTap);

  @override
  Widget build(BuildContext context) {
    var applicationsModel =
        Provider.of<ApplicationsModel>(context, listen: false);

    if (_timer != null) this._timer.cancel();
    this._timer = Timer.periodic(Duration(seconds: 100), (timer) {
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
    this._timer.cancel();
  }
}



class TopApps extends StatefulWidget {
  final Function onTap;

  const TopApps({Key key, this.onTap}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return _TopApps(onTap);
  }
}

class AppItem extends StatelessWidget {
  const AppItem({
    Key key,
    @required this.appDetail,
    @required this.onTap,
    @required this.onLongPress,
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
        child: Row(children: <Widget>[
          Padding(
            child: Text(
              appDetail.label,
              style: Theme.of(context).textTheme.headline6,
              overflow: TextOverflow.ellipsis,
            ),
            padding: EdgeInsets.all(10),
          ),
          new Image.memory(appDetail.icon,
              fit: BoxFit.scaleDown, width: 48.0, height: 48.0)
        ], mainAxisAlignment: MainAxisAlignment.spaceBetween),
        padding: EdgeInsets.all(8.0),
      ),
    );
  }
}
