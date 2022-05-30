import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:neurhome/application.dart';
import 'package:provider/provider.dart';

import 'data/applications_model.dart';
import 'data/favorites_model.dart';

class ShortcutIcon extends StatelessWidget {
  final int index;
  final IconData icon;
  final Application currentApp;

  const ShortcutIcon(this.icon,
      {Key? key, required this.index, required this.currentApp})
      : super(key: key);

  @override
  Widget build(BuildContext context) {
    Application? shortcut =
        context.select<ShortcutsModel, Application?>((sm) => sm.apps[index]);

    log("${context.read<ShortcutsModel>().apps[index]}");

    return GestureDetector(
      onTap: () =>
          context.read<ShortcutsModel>().setShortcut(index, currentApp),
      child: Stack(children: [
        Icon(icon),
        if (shortcut != null)
          Positioned(
            right: 0,
            bottom: 0,
            child: Image.memory(
                context.read<ShortcutsModel>().apps[index]!.icon,
                fit: BoxFit.scaleDown,
                width: 16.0,
                height: 16.0),
          )
      ]),
    );
  }
}

class AppItem extends StatefulWidget {
  const AppItem({
    key,
    required this.appDetail,
  }) : super(key: key);

  final Application appDetail;

  @override
  State<AppItem> createState() => _AppItemState();
}

class _AppItemState extends State<AppItem> {
  bool _isOpen = false;

  void _toggleSubMenu() {
    setState(() {
      _isOpen = !_isOpen;
    });
  }

  @override
  Widget build(BuildContext context) {
    var applications = context.read<ApplicationsModel>();
    var isVisible = context.select<ApplicationsModel, bool>(
        (am) => am.isVisible(widget.appDetail.package));

    return GestureDetector(
      onTap: () async => !_isOpen
          ? applications.launchApp(widget.appDetail)
          : _toggleSubMenu(),
      onLongPress: _toggleSubMenu,
      child: Padding(
        padding: (_isOpen ? const EdgeInsets.all(8.0) : EdgeInsets.zero),
        child: Container(
          decoration: BoxDecoration(
            color: Colors.transparent,
            border: _isOpen ? Border.all(color: Colors.grey) : null,
          ),
          child: Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
              children: [
                Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: <Widget>[
                      Padding(
                        padding: const EdgeInsets.all(10),
                        child: Text(
                          widget.appDetail.label,
                          style: Theme.of(context).textTheme.headline6,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Image.memory(widget.appDetail.icon,
                          fit: BoxFit.scaleDown, width: 48.0, height: 48.0)
                    ]),
                if (_isOpen)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(10, 10, 0, 10),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        IconButton(
                          onPressed: () => context
                              .read<ApplicationsModel>()
                              .toggle(widget.appDetail.package),
                          icon: Icon(
                            isVisible ? Icons.visibility : Icons.visibility_off,
                            size: 24,
                          ),
                        ),
                        IconButton(
                            onPressed: () {
                              context
                                .read<ApplicationsModel>()
                                .removeApp(widget.appDetail.package);
                              _toggleSubMenu();
                            },
                            icon: const Icon(Icons.delete)),
                        ShortcutsButton(application: widget.appDetail),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class ShortcutsButton extends StatelessWidget {
  const ShortcutsButton({
    Key? key,
    required this.application,
  }) : super(key: key);

  final Application application;

  static const icons = [
    Icons.looks_one,
    Icons.looks_two,
    Icons.looks_3,
    Icons.looks_4
  ];

  @override
  Widget build(BuildContext context) {
    return Row(
        children: icons
            .asMap()
            .entries
            .map((entry) => ShortcutIcon(entry.value,
                index: entry.key, currentApp: application))
            .toList());
  }
}
