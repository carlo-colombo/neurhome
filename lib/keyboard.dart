import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';

import 'data/applications_model.dart';
import 'key_cap.dart';

final initials = <String>[
  "0-9",
  "abc",
  "def",
  "ghi",
  "jkl",
  "mno",
  "pqrs",
  "tuv",
  "wxyz"
];

class Keyboard extends StatelessWidget {
  const Keyboard({
    Key key,
    @required this.applicationModel,
  }) : super(key: key);

  final ApplicationsModel applicationModel;

  @override
  Widget build(BuildContext context) {
    return Container(
        child: Wrap(
      alignment: WrapAlignment.center,
      children: <Widget>[
        ...initials
            .toList()
            .map((letter) => KeyCap(
                child: Text(
                  letter,
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                ),
                onTap: () => applicationModel.addToQuery(letter)))
            .toList(),
        KeyCap(
          border: false,
          child: Icon(Icons.backspace, size: 32),
          onTap: () => applicationModel.popQuery(),
        )
      ],
    ));
  }
}
