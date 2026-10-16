import 'package:flick/shared/widgets/flick_badges.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('FlickAvatar', () {
    testWidgets('ne crashe jamais avec un nom null (visiteur déconnecté)',
        (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: FlickAvatar(name: null)),
      ));
      expect(find.text('?'), findsOneWidget);
    });

    testWidgets('nom vide → repli « ? »', (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: FlickAvatar(name: '   ')),
      ));
      expect(find.text('?'), findsOneWidget);
    });

    testWidgets('affiche l’initiale sinon', (tester) async {
      await tester.pumpWidget(const MaterialApp(
        home: Scaffold(body: FlickAvatar(name: 'zekatv')),
      ));
      expect(find.text('Z'), findsOneWidget);
    });
  });
}
