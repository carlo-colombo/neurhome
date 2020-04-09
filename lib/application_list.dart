import 'package:flutter/material.dart';

class AppList extends StatelessWidget {
  final List installedAppDetails;
  final Function onTap;
  final Future<void> Function(String package) removeApplication;

  AppList(this.installedAppDetails, this.onTap, this.removeApplication);

  @override
  Widget build(BuildContext context) {
    return Flexible(
        child: ListView.builder(
      itemBuilder: (context, i) {
        var appDetail = installedAppDetails[i];
        return new GestureDetector(
          onTap: () => onTap(appDetail),
          onLongPress: () => removeApplication(appDetail.package),
          child: AppItem(appDetail: appDetail),
        );
      },
      itemCount: installedAppDetails.length,
    ));
  }
}

class ReducedAppList extends StatelessWidget {
  final List installedAppDetails;
  final Function onTap;

  ReducedAppList(this.installedAppDetails, this.onTap);

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.start,
      children: installedAppDetails
          .map((ad) => GestureDetector(
              onTap: () => onTap(ad), child: AppItem(appDetail: ad)))
          .toList(),
    );
  }
}

class AppItem extends StatelessWidget {
  const AppItem({
    Key key,
    @required this.appDetail,
  }) : super(key: key);

  final appDetail;

  @override
  Widget build(BuildContext context) {
    return Padding(
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
    );
  }
}
