import 'package:flick/core/network/api_exception.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('ApiException', () {
    test('parse enveloppe erreur backend', () {
      final e = ApiException.fromJson(400, {
        'success': false,
        'errorCode': 'VALIDATION',
        'message': 'Email invalide',
        'errors': {'email': 'doit être valide'},
      });
      expect(e.status, 400);
      expect(e.message, 'Email invalide');
      expect(e.errorCode, 'VALIDATION');
      expect(e.fields, {'email': 'doit être valide'});
    });

    test('helpers statut', () {
      expect(ApiException(401, 'x').isAuth, isTrue);
      expect(ApiException(429, 'x').isRateLimited, isTrue);
      expect(ApiException(500, 'x').isServer, isTrue);
      expect(ApiException.network().status, -1);
    });
  });
}
