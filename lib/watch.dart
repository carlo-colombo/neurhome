import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:neurhome/app_config.dart';
import 'package:provider/provider.dart';

class Watch extends StatefulWidget {
  final MethodChannel platform;

  const Watch(this.platform);

  @override
  State<StatefulWidget> createState() {
    return _Watch(platform);
  }
}

const duration = Duration(seconds: 1);

var time = DateFormat.Hm();
var date = DateFormat.yMMMd();

class _Watch extends State<Watch> {
  late Timer _timer;
  late DateTime dateTime;
  late MethodChannel platform;

  _Watch(this.platform);

  @override
  void initState() {
    super.initState();
    dateTime = DateTime.now();
    _timer = Timer.periodic(duration, setTime);
  }

  void setTime(Timer timer) {
    setState(() {
      dateTime = DateTime.now();
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    var headline3 =
        Theme.of(context).textTheme.headline3?.copyWith(color: Colors.white);

    return GestureDetector(
        onTap: () => platform.invokeMethod("openClock"),
        child: Column(children: <Widget>[
          Text(time.format(dateTime) + context.read<AppConfig>().profile, style: headline3),
          Text(date.format(dateTime)),
        ]));
  }
}
