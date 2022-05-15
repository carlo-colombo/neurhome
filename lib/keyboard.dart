import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:neurhome/data/applications_model.dart';
import 'package:provider/provider.dart';

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
  final backspace;
  final addToQuery;

  const Keyboard({key, required this.backspace, required this.addToQuery})
      : super(key: key);

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
                  style: const TextStyle(
                      fontSize: 24, fontWeight: FontWeight.bold),
                ),
                onTap: () => addToQuery(letter)))
            .toList(),
        KeyCap(
          border: false,
          child: const Icon(Icons.backspace, size: 32),
          onTap: () => backspace(),
        )
      ],
    ));
  }
}

class KeyboardContainer extends StatelessWidget {
  const KeyboardContainer({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<ApplicationsModel>(
        builder: (context, applications, child) => Keyboard(
              backspace: applications.popQuery,
              addToQuery: applications.addToQuery,
            ));
  }
}
