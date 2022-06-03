import 'package:flutter/cupertino.dart';
import 'package:provider/provider.dart';
import 'package:wifi_info_flutter/wifi_info_flutter.dart';

import '../application_list.dart';
import '../bottom_bar.dart';
import '../data/applications_model.dart';
import '../inputs/keyboard.dart';
import '../inputs/query.dart';
import '../watch.dart';
import 'base_layout.dart';

class Home extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () => Future.value(false),
      child: BaseLayout(
        children: [
          const Watch(),
          Expanded(
              child: Consumer<ApplicationsModel>(
            builder: (a, applications, c) => Column(
              mainAxisAlignment: applications.query.isEmpty
                  ? MainAxisAlignment.start
                  : MainAxisAlignment.end,
              verticalDirection: VerticalDirection.down,
              children: applications.query.isEmpty
                  ? [const TopApps(), Container()]
                  : [
                      ReducedAppList(reverse: applications.query.isNotEmpty),
                      Query(
                          query: applications.query,
                          onPressed: () => applications.clearQuery())
                    ],
            ),
          )),
          const KeyboardContainer(),
          const BottomBar(),
        ],
      ),
    );
  }
}
