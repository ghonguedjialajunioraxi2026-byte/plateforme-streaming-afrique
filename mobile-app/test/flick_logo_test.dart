import 'package:flick/shared/widgets/flick_logo.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('FlickLogo affiche le logo officiel', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: FlickLogo(height: 32))),
    );
    expect(find.byType(Image), findsOneWidget);
    expect(find.bySemanticsLabel('FLICK'), findsOneWidget);
  });
}
