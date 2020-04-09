import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

class Watch extends StatefulWidget {
  MethodChannel platform;

  Watch(this.platform);

  @override
  State<StatefulWidget> createState() {
    return _Watch(platform);
  }
}

const duration = const Duration(seconds: 1);

var time = new DateFormat.Hm();
var date = new DateFormat.yMMMd();

class _Watch extends State<Watch> {
  Timer _timer;
  DateTime dateTime;
  MethodChannel platform;

  _Watch(this.platform);

  @override
  void initState() {
    super.initState();
    dateTime = new DateTime.now();
    this._timer = new Timer.periodic(duration, setTime);
  }

  void setTime(Timer timer) {
    setState(() {
      dateTime = new DateTime.now();
    });
  }

  @override
  void dispose() {
    _timer.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
        onTap: () {
          platform.invokeMethod("openClock");
        },
        child: Column(children: <Widget>[
          Text(
            time.format(dateTime),
            style: Theme.of(context)
                .textTheme
                .display2
                .merge(TextStyle(color: Colors.white)),
          ),
          Text(date.format(dateTime)),
        ]));
  }
}
