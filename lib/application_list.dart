import 'dart:async';

import 'package:flutter/material.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:provider/provider.dart';

import 'app_item.dart';

class AppList extends StatelessWidget {
  const AppList();

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
      builder: (context, applications, child) => Flexible(
          child: ListView.builder(
        itemBuilder: (context, i) {
          var appDetail = applications.installed[i];

          return AppItem(
            appDetail: appDetail,
          );
        },
        itemCount: applications.installed.length,
      )),
    );
  }
}

class ReducedAppList extends StatelessWidget {
  final bool reverse;

  const ReducedAppList({required this.reverse});

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
      builder: (context, applications, child) {
        var apps =
            reverse ? applications.filtered.reversed : applications.filtered;

        return Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: apps.map((ad) => AppItem(appDetail: ad)).toList(),
        );
      },
    );
  }
}

class _TopApps extends State<TopApps> {
  Timer? _timer;

  _TopApps();

  @override
  Widget build(BuildContext context) {
    var applicationsModel =
        Provider.of<ApplicationsModel>(context, listen: false);

    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 25), (timer) {
      applicationsModel.updateTopApps();
    });

    return ReducedAppList(
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
  const TopApps({key}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return _TopApps();
  }
}
