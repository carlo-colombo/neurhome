import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mockito/mockito.dart';
import 'package:neurhone/data/applications_model.dart';
import 'package:neurhone/data/db.dart';

void main() {
  group('ApplicationModel', () {
    var mockPlatform;
    var model;

    setUp(() {
      mockPlatform = MockPlatform();
      model = ApplicationsModel(mockPlatform, MockDB());
    });

    test('list and sort applications', () {
      when(mockPlatform.invokeListMethod("listApps", any))
          .thenAnswer((_) => Future.value([
                {"package": "com.b", "label": "B"},
                {"package": "com.a", "label": "a"},
                {"package": "com.d", "label": "d"},
                {"package": "com.c", "label": "Google c"},
              ]));

      model.addListener(() {
        expect(
            model.installed.map((a) => a.label), equals(['a', 'B', 'c', 'd']));
      });
      model.updateInstalled();
      model.updateInstalled();
    });

    test('get tops apps', () {
      when(mockPlatform.invokeListMethod("listTopApps", any))
          .thenAnswer((_) => Future.value([
                {"package": "com.d", "label": "d"},
                {"package": "com.c", "label": "Google c"},
                {"package": "com.a", "label": "a"},
              ]));

      model.addListener(() {
        expect(model.filtered.map((a) => a.label), equals(['c', 'a', 'd']));
      });
      model.updateTopApps();
      model.updateTopApps();
    });

    test('uninstall apps', () async {
      when(mockPlatform.invokeMethod("removeApp", any))
          .thenAnswer((_) => Future.value(true));
      when(mockPlatform.invokeListMethod("listApps", any))
          .thenAnswer((_) => Future.value([
                {"package": "com.b", "label": "B"},
                {"package": "com.a", "label": "a"},
              ]));

      await model.updateInstalled();

      model.addListener(() {
        verify(mockPlatform.invokeMethod("removeApp", <String, dynamic>{
          'package': 'com.a',
        }));
        expect(model.installed.map((a) => a.package), equals(["com.b"]));
      });

      model.remove("com.a");
    });

    test('filters apps by query', () async {
      when(mockPlatform.invokeListMethod("listApps", any))
          .thenAnswer((_) => Future.value([
                {"package": "com.b", "label": "B"},
                {"package": "com.a", "label": "a"},
                {"package": "com.d", "label": "d"},
                {"package": "com.c", "label": "Google c"},
              ]));

      await model.updateInstalled();

      model.addListener(() {
        expect(model.query, equals('[dc]'));
        expect(
            model.filtered.map((a) => a.package), equals(["com.c", "com.d"]));
      });

      model.addToQuery('[dc]');
    });

    test('pop from query', () async {
      when(mockPlatform.invokeListMethod("listTopApps", any))
          .thenAnswer((_) => Future.value([
                {"package": "com.c", "label": "Google c"},
                {"package": "com.a", "label": "a"},
                {"package": "com.d", "label": "d"},
              ]));

      model.addToQuery('[dc]');

      model.addListener(() {
        expect(model.query, equals(''));
        expect(model.filtered.map((a) => a.label), equals(['c', 'a', 'd']));
      });

      model.popQuery();
    });
  });
}

class MockPlatform extends Mock implements MethodChannel {}

class MockDB extends Mock implements DB {
  @override
  Future<List<Map<String, dynamic>>> topApps() async {
    return Future.value([
      {"package": "com.d", "count": 2},
      {"package": "com.B", "count": 0},
      {"package": "com.a", "count": 5},
      {"package": "com.c", "count": 42},
    ]);
  }
}
