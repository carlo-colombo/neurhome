import 'package:flutter/material.dart';

class Query extends StatelessWidget {
  const Query({
    key,
    required this.query,
    required this.onPressed,
  }) : super(key: key);

  final String query;
  final void Function() onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(10),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Flexible(
              child: Text(
            query,
            style: Theme.of(context).textTheme.titleMedium,
            overflow: TextOverflow.ellipsis,
          )),
          IconButton(
              onPressed: onPressed,
              icon: const Icon(Icons.cancel, size: 40, color: Colors.white)),
        ],
      ),
    );
  }
}
