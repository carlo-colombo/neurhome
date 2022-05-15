import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'data/stats_model.dart';

class StatsPage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Theme(
      data: Theme.of(context).copyWith(
          textTheme:
              Typography.material2018(platform: TargetPlatform.android).black),
      child: Consumer<StatsModel>(
          builder: (a, stats, c) => DefaultTabController(
                length: 2,
                child: Scaffold(
                    appBar: AppBar(
                      title: const Text('Stats'),
                      actions: [
                        IconButton(
                            icon: const Icon(Icons.refresh),
                            onPressed: () => stats.update())
                      ],
                      bottom: const TabBar(
                        tabs: [Text("Last opened"), Text("Top apps")],
                      ),
                    ),
                    body: TabBarView(
                      children: [
                        Table(
                            border: TableBorder.all(),
                            children: stats.lastOpened
                                .map((e) => TableRow(children: [
                                      TableCell(
                                          child: Text(
                                        e["package"],
                                      )),
                                      TableCell(
                                          child: Center(
                                              child: Text(
                                        e["timestamp"],
                                      )))
                                    ]))
                                .toList()),
                        Table(
                            border: TableBorder.all(),
                            children: stats.topApps
                                .map((e) => TableRow(children: [
                                      TableCell(
                                          child: Text(
                                        e["package"],
                                      )),
                                      TableCell(
                                          child: Center(
                                              child: Text(
                                        "${e["count"]}",
                                      )))
                                    ]))
                                .toList())
                      ],
                    )),
              )),
    );
  }
}
