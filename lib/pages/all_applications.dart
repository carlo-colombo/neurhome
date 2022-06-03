import 'dart:developer';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../application_list.dart';
import '../data/db.dart';
import 'base_layout.dart';
import 'stats.dart';

class AllApplications extends StatelessWidget {
  const AllApplications({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BaseLayout(children: [
      Padding(
          padding: const EdgeInsets.all(10.0),
          child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: <Widget>[
                IconButton(
                    onPressed: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                            builder: (context) => const StatsPage())),
                    icon: const Icon(Icons.table_chart)),
                Row(
                  children: [
                    IconButton(
                        onPressed: () async {
                          Share.shareFiles([await DB().dbPath]);
                        },
                        icon: Icon(Icons.share)),
                    IconButton(
                        onPressed: pickDatabase,
                        icon: const Icon(Icons.upload_sharp)),
                  ],
                )
              ])),
      const AppList()
    ]);
  }

  pickDatabase() async {
    FilePickerResult? result =
        await FilePicker.platform.pickFiles(type: FileType.any);
    if (result == null) {
      return log("selected file: ${result?.files.first.path.toString()}");
    }
    await DB.instance.reload(result.files.first.path!);
  }
}
