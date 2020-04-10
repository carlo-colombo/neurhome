import 'dart:ui';

import 'package:flutter/material.dart';

class DrawingPoints {
  Paint paint;
  Offset points;

  DrawingPoints({this.points, this.paint});
}

class DrawingPainter extends CustomPainter {
  DrawingPainter({this.pointsList, this.exportImageCanvas});

  Canvas exportImageCanvas;
  List<DrawingPoints> pointsList;
  List<Offset> offsetPoints = List();
  Paint paintScreen = Paint()
    ..strokeCap = StrokeCap.round
    ..isAntiAlias = true
    ..color = Colors.white
    ..strokeWidth = 10;
  Paint paintImage = Paint()
    ..strokeCap = StrokeCap.round
    ..isAntiAlias = true
    ..color = Colors.black
    ..strokeWidth = 10;

  @override
  void paint(Canvas canvas, Size size) {
    for (int i = 0; i < pointsList.length - 1; i++) {
      if (pointsList[i] != null && pointsList[i + 1] != null) {
        exportImageCanvas.drawLine(
            pointsList[i].points, pointsList[i + 1].points, paintImage);
        canvas.drawLine(
            pointsList[i].points, pointsList[i + 1].points, paintScreen);
      } else if (pointsList[i] != null && pointsList[i + 1] == null) {
        offsetPoints.clear();
        offsetPoints.add(pointsList[i].points);
        offsetPoints.add(Offset(
            pointsList[i].points.dx + 0.1, pointsList[i].points.dy + 0.1));

        canvas.drawPoints(PointMode.points, offsetPoints, paintScreen);
        exportImageCanvas.drawPoints(PointMode.points, offsetPoints, paintImage);
      }
    }
  }

  @override
  bool shouldRepaint(DrawingPainter oldDelegate) => true;
}
