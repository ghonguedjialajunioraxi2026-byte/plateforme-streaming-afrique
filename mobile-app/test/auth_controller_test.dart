import 'dart:convert';

import 'package:flick/core/network/api_client.dart';
import 'package:flick/features/auth/auth_controller.dart';
import 'package:flick/features/auth/auth_repository.dart';
import 'package:flick/features/auth/google_sign_in_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _Tokens implements TokenStore {
  @override
  String? accessToken;
  String? _refresh;

  @override
  Future<String?> readRefresh() async => _refresh;

  @override
  Future<void> writeRefresh(String? v) async => _refresh = v;
}

http.Response _loginOk() => http.Response(
    jsonEncode({
      'success': true,
      'message': 'ok',
      'data': {
        'accessToken': 'A',
        'tokenType': 'Bearer',
        'expiresIn': 900,
        'userId': 7,
        'email': 'ada@example.com',
        'displayName': 'Ada',
        'role': 'USER',
        'refreshToken': 'R',
        'refreshExpiresIn': 604800,
      },
      'timestamp': ''
    }),
    200);

http.Response _loginKo() => http.Response(
    jsonEncode({
      'success': false,
      'status': 401,
      'errorCode': 'BAD_CREDENTIALS',
      'message': 'Identifiants invalides',
      'path': '/api/auth/login',
      'timestamp': ''
    }),
    401);

http.Response _googleConflict() => http.Response(
    jsonEncode({
      'success': false,
      'status': 409,
      'errorCode': 'CONFLICT',
      'message': 'Email déjà pris par un compte local',
      'path': '/api/auth/google',
      'timestamp': ''
    }),
    409);

/// Faux flux Google natif (le plugin n'est pas testable en unitaire).
class _FakeGoogle implements GoogleIdTokenProvider {
  _FakeGoogle({this.idToken, this.failure});

  final String? idToken;
  final GoogleSignInException? failure;

  @override
  Future<String?> signIn() async {
    if (failure != null) throw failure!;
    return idToken;
  }

  @override
  Future<void> signOut() async {}
}

AuthController _googleAuth(
  MockClient inner, {
  String? idToken = 'google-id-token',
  GoogleSignInException? failure,
}) {
  final api = ApiClient(tokens: _Tokens(), inner: inner);
  return AuthController(
    AuthRepository(api),
    googleSignIn: _FakeGoogle(idToken: idToken, failure: failure),
  );
}

void main() {
  group('AuthController', () {
    test('login OK → authentifié, logout → purge', () async {
      final tokens = _Tokens();
      final api = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async =>
            req.url.path.endsWith('/auth/login') ? _loginOk() : _loginKo()),
      );
      final auth = AuthController(AuthRepository(api));
      expect(auth.isAuthenticated, isFalse);
      expect(await auth.login('ada@example.com', 'MotDePasse123'), isTrue);
      expect(auth.isAuthenticated, isTrue);
      expect(auth.user?.email, 'ada@example.com');
      await auth.logout();
      expect(auth.isAuthenticated, isFalse);
      expect(tokens.accessToken, isNull);
      expect(await tokens.readRefresh(), isNull);
    });

    test('login 401 → erreur, reste déconnecté', () async {
      final api = ApiClient(
        tokens: _Tokens(),
        inner: MockClient((_) async => _loginKo()),
      );
      final auth = AuthController(AuthRepository(api));
      expect(await auth.login('x@y.z', 'wrongpass1'), isFalse);
      expect(auth.isAuthenticated, isFalse);
      expect(auth.error, isNotNull);
    });

    test('Google OK → authentifié (id_token posté, session stockée)', () async {
      String? postedIdToken;
      final auth = _googleAuth(MockClient((req) async {
        if (!req.url.path.endsWith('/auth/google')) return _loginKo();
        postedIdToken =
            (jsonDecode(req.body) as Map<String, dynamic>)['idToken']
                ?.toString();
        return _loginOk();
      }));
      expect(await auth.loginWithGoogle(), isTrue);
      expect(auth.isAuthenticated, isTrue);
      expect(auth.user?.email, 'ada@example.com');
      expect(postedIdToken, 'google-id-token');
    });

    test('Google annulé → silencieux (ni session, ni erreur)', () async {
      var called = false;
      final auth = _googleAuth(
        MockClient((_) async {
          called = true;
          return _loginOk();
        }),
        idToken: null,
      );
      expect(await auth.loginWithGoogle(), isFalse);
      expect(auth.isAuthenticated, isFalse);
      expect(auth.error, isNull);
      expect(called, isFalse);
    });

    test('Google 409 (compte local) → erreur affichée, déconnecté', () async {
      final auth = _googleAuth(
          MockClient((_) async => _googleConflict()));
      expect(await auth.loginWithGoogle(), isFalse);
      expect(auth.isAuthenticated, isFalse);
      expect(auth.error, contains('compte local'));
    });

    test('Google plugin en échec → erreur affichée, déconnecté', () async {
      final auth = _googleAuth(
        MockClient((_) async => _loginOk()),
        failure: const GoogleSignInException('réseau indisponible'),
      );
      expect(await auth.loginWithGoogle(), isFalse);
      expect(auth.isAuthenticated, isFalse);
      expect(auth.error, contains('réseau'));
    });
  });
}
