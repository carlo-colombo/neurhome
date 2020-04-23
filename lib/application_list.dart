import 'package:flutter/material.dart';
import 'package:neurhone/data/applications_model.dart';
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
            onLongPress: () => applications.remove(appDetail.package),
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

  ReducedAppList(this.onTap);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
      builder: (context, applications, child) {
        return Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: applications.filtered
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
              style: Theme.of(context).textTheme.title,
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
