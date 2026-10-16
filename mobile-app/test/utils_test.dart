import 'package:flick/core/utils/format.dart';
import 'package:flick/core/utils/validators.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('formatCount', () {
    test('petites valeurs inchangées', () {
      expect(formatCount(0), '0');
      expect(formatCount(999), '999');
    });
    test('milliers', () {
      expect(formatCount(1000), '1 k');
      expect(formatCount(1500), '1.5 k');
    });
    test('millions', () {
      expect(formatCount(2000000), '2 M');
    });
  });

  group('formatDuration', () {
    test('mm:ss', () {
      expect(formatDuration(0), '0:00');
      expect(formatDuration(65), '1:05');
      expect(formatDuration(754), '12:34');
    });
  });

  group('validators', () {
    test('email', () {
      expect(validateEmail(''), isNotNull);
      expect(validateEmail('nope'), isNotNull);
      expect(validateEmail('ada@example.com'), isNull);
    });
    test('username (règles backend)', () {
      expect(validateUsername('ab'), isNotNull);
      expect(validateUsername('mauvais pseudo!'), isNotNull);
      expect(validateUsername('user237'), isNull);
    });
    test('password (8+, lettre + chiffre)', () {
      expect(validatePassword('court1'), isNotNull);
      expect(validatePassword('sansChiffre'), isNotNull);
      expect(validatePassword('MotDePasse123'), isNull);
    });
  });
}
