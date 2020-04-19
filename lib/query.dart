import 'package:flutter/material.dart';

//
//class Query extends StatelessWidget{
//
//  @override
//  Widget build(BuildContext context) {
//    return
//  }
//}

// ignore: non_constant_identifier_names
//Widget Query({onPressed}) => Consumer<ApplicationsModel>(
//    builder: (_, applications, __) {
//      return _Query(query: applications.query, onPressed: onPressed);
//    });

class Query extends StatelessWidget {
  const Query({
    Key key,
    @required this.query,
    @required this.onPressed,
  }) : super(key: key);

  final String query;
  final void Function() onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      child: Row(
        children: [
          Flexible(
              child: Text(
            query,
            style: Theme.of(context).textTheme.title,
            overflow: TextOverflow.ellipsis,
          )),
          IconButton(
              onPressed: onPressed,
              icon: Icon(Icons.cancel, size: 40, color: Colors.white)),
        ],
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
      ),
      padding: EdgeInsets.all(10),
    );
  }
}
