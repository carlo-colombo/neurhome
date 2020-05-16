import 'package:flutter/material.dart';

class KeyCap extends StatelessWidget {
  const KeyCap(
      {Key key, @required this.child, @required this.onTap, this.border = true})
      : super(key: key);

  final Widget child;
  final Null Function() onTap;
  final bool border;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
        onTap: onTap,
        child: Container(
          child: Center(child: child),
          margin: EdgeInsets.all(4),
          height: 52,
          width: 64,
          decoration: BoxDecoration(
              border: border ? Border.all(color: Colors.grey) : null,
              borderRadius: BorderRadius.circular(5)),
        ));
  }
}
