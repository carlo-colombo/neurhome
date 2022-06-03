import 'dart:async';

import 'package:flutter/material.dart';
import 'package:neurhome/application.dart';
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

  const ReducedAppList({Key? key, required this.reverse}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.start,
      children: List.generate(
          6, (index) => AppItemSelector(index: reverse ? 5 - index : index)),
    );
  }
}

class AppItemSelector extends StatelessWidget {
  final int index;

  const AppItemSelector({Key? key, required this.index}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Selector<ApplicationsModel, Application?>(builder: (_, app, __) {
      if (app != null) {
        return AppItem(appDetail: app);
      }
      return Container();
    }, selector: (_, am) {
      return index < am.filtered.length ? am.filtered[index] : null;
    });
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

    return const ReducedAppList(
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
