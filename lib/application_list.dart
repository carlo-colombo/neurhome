import 'package:flutter/material.dart';

class AppList extends StatelessWidget {
  final List installedAppDetails;
  final Function onTap;

  AppList(this.installedAppDetails, this.onTap);

  @override
  Widget build(BuildContext context) {
    return Flexible(
        child: ListView.builder(
      itemBuilder: (context, i) {
        var icon = new Image.memory(installedAppDetails[i].icon,
            fit: BoxFit.scaleDown, width: 48.0, height: 48.0);

        var label = Padding(
          child: Text(installedAppDetails[i].label),
          padding: EdgeInsets.all(10),
        );

        return new GestureDetector(
          onTap: ()=> onTap(installedAppDetails[i].package),
          child: Padding(
            child: Row(children: <Widget>[icon, label]),
            padding: EdgeInsets.all(8.0),
          ),
        );
      },
      itemCount: installedAppDetails.length,
    ));
  }
}