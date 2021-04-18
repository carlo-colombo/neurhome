import 'dart:async';

import 'package:flutter/material.dart';
import 'package:vibration/vibration.dart';

class KeyCap extends StatefulWidget {
  const KeyCap(
      {Key key, @required this.child, @required this.onTap, this.border = true})
      : super(key: key);

  final Widget child;
  final Null Function() onTap;
  final bool border;

  @override
  _KeyCapState createState() => _KeyCapState();
}

class _KeyCapState extends State<KeyCap> {
  bool pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
        onTapDown: (_) {
          setState(() => pressed = true);
          Timer(Duration(seconds: 1), () => setState(() => pressed = false));
          return Vibration.vibrate(duration: 50);
        },
        onTapUp: (_) => setState(() => pressed = false),
        onTap: widget.onTap,
        child: Container(
          child: Center(child: widget.child),
          margin: EdgeInsets.all(4),
          height: 52,
          width: 64,
          decoration: BoxDecoration(
              color:
                  pressed ? Theme.of(context).accentColor : Colors.transparent,
              border: widget.border ? Border.all(color: Colors.grey) : null,
              borderRadius: BorderRadius.circular(5)),
        ));
  }
}
