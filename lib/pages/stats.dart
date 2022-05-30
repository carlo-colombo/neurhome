import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/stats_model.dart';

class StatsPage extends StatelessWidget {
  const StatsPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: Theme.of(context).copyWith(
          textTheme:
              Typography.material2018(platform: TargetPlatform.android).black),
      child: DefaultTabController(
        length: 2,
        child: Scaffold(
            appBar: AppBar(
              title: const Text('Stats'),
              actions: [
                IconButton(
                    icon: const Icon(Icons.refresh),
                    onPressed: () => context.read<StatsModel>().update())
              ],
              bottom: const TabBar(
                tabs: [Text("Last opened"), Text("Top apps")],
              ),
            ),
            body: const TabBarView(
              children: [LastOpened(), TopApps()],
            )),
      ),
    );
  }
}

class TopApps extends StatelessWidget {
  const TopApps({
    Key? key,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Table(
        border: TableBorder.all(),
        children: context
            .read<StatsModel>()
            .topApps
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
            .toList());
  }
}

class LastOpened extends StatelessWidget {
  const LastOpened({
    Key? key,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Table(
        border: TableBorder.all(),
        children: context
            .read<StatsModel>()
            .lastOpened
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
            .toList());
  }
}
