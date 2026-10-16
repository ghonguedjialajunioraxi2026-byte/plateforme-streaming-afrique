import 'dart:convert';

import 'package:flick/core/network/api_client.dart';
import 'package:flick/core/network/api_exception.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// TokenStore mémoire pour les tests.
class _MemoryTokens implements TokenStore {
  @override
  String? accessToken;
  String? _refresh;

  @override
  Future<String?> readRefresh() async => _refresh;

  @override
  Future<void> writeRefresh(String? value) async => _refresh = value;
}

http.Response _ok(Object data) => http.Response(
    jsonEncode(
        {'success': true, 'message': 'ok', 'data': data, 'timestamp': ''}),
    200);

http.Response _err(int status, String code, String message) =>
    http.Response(
        jsonEncode({
          'success': false,
          'status': status,
          'errorCode': code,
          'message': message,
          'path': '/x',
          'timestamp': ''
        }),
        status);

void main() {
  group('ApiClient', () {
    test('déballe data + Authorization', () async {
      String? auth;
      final tokens = _MemoryTokens()..accessToken = 'A1';
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          auth = req.headers['Authorization'];
          return _ok({'hello': 'world'});
        }),
      );
      final data = await client.get<Map<String, dynamic>>(
          '/x', (j) => j as Map<String, dynamic>);
      expect(data['hello'], 'world');
      expect(auth, 'Bearer A1');
    });

    test('401 → refresh unique puis retry', () async {
      var refreshCalls = 0;
      final tokens = _MemoryTokens()
        ..accessToken = 'OLD'
        ..writeRefresh('R1');
      await tokens.writeRefresh('R1');
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/auth/refresh')) {
            refreshCalls++;
            return _ok({
              'accessToken': 'NEW',
              'tokenType': 'Bearer',
              'expiresIn': 900,
              'refreshToken': 'R2',
              'refreshExpiresIn': 604800,
            });
          }
          if (req.headers['Authorization'] == 'Bearer NEW') {
            return _ok('retry-ok');
          }
          return _err(401, 'TOKEN_EXPIRED', 'expiré');
        }),
      );
      // Deux 401 concurrents → UN seul refresh (single-flight).
      final results = await Future.wait([
        client.get<String>('/a', (j) => j.toString()),
        client.get<String>('/b', (j) => j.toString()),
      ]);
      expect(results, ['retry-ok', 'retry-ok']);
      expect(refreshCalls, 1);
      expect(tokens.accessToken, 'NEW');
      expect(await tokens.readRefresh(), 'R2');
    });

    test('refresh impossible → session expirée, pas de boucle', () async {
      var expired = 0;
      var refreshCalls = 0;
      final tokens = _MemoryTokens()
        ..accessToken = 'OLD'
        ..writeRefresh('BAD');
      await tokens.writeRefresh('BAD');
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/auth/refresh')) {
            refreshCalls++;
            return _err(401, 'INVALID', 'refresh invalide');
          }
          return _err(401, 'TOKEN_EXPIRED', 'expiré');
        }),
        onSessionExpired: () => expired++,
      );
      await expectLater(client.get<String>('/a', (j) => '$j'),
          throwsA(isA<ApiException>()));
      expect(refreshCalls, 1); // pas de boucle infinie
      expect(expired, 1);
      expect(tokens.accessToken, isNull);
    });

    test('jamais de refresh sur /auth/login', () async {
      var refreshCalls = 0;
      final tokens = _MemoryTokens()..accessToken = 'OLD';
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/auth/refresh')) {
            refreshCalls++;
            return _ok({});
          }
          return _err(401, 'BAD_CREDENTIALS', 'identifiants');
        }),
      );
      await expectLater(
          client.post<String>('/auth/login', (j) => '$j',
              body: {}, auth: false),
          throwsA(isA<ApiException>()));
      expect(refreshCalls, 0);
    });

    test('coupure réseau → ApiException.network', () async {
      final client = ApiClient(
        tokens: _MemoryTokens(),
        inner: MockClient((_) async => throw Exception('down')),
      );
      try {
        await client.get<String>('/a', (j) => '$j', auth: false);
        fail('aurait dû lever');
      } on ApiException catch (e) {
        expect(e.status, -1);
      }
    });

    test('PATCH conserve le verbe + query au retry après 401', () async {
      String? retriedMethod;
      String? retriedQuery;
      final tokens = _MemoryTokens()..accessToken = 'OLD';
      await tokens.writeRefresh('R1');
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/auth/refresh')) {
            return _ok({
              'accessToken': 'NEW',
              'tokenType': 'Bearer',
              'expiresIn': 900,
              'refreshToken': 'R2',
              'refreshExpiresIn': 604800,
            });
          }
          if (req.headers['Authorization'] == 'Bearer NEW') {
            retriedMethod = req.method;
            retriedQuery = req.url.query;
            return _ok({'isRead': true});
          }
          return _err(401, 'TOKEN_EXPIRED', 'expiré');
        }),
      );
      await client.patch<Map<String, dynamic>>(
          '/notifications/7/read', (j) => (j as Map?)?.cast<String, dynamic>() ?? {});
      // Le retry rejoue PATCH (pas GET/POST) sur la même URI.
      expect(retriedMethod, 'PATCH');
      expect(retriedQuery, isEmpty);
    });

    test('GET retry conserve les query params', () async {
      String? retriedQuery;
      final tokens = _MemoryTokens()..accessToken = 'OLD';
      await tokens.writeRefresh('R1');
      final client = ApiClient(
        tokens: tokens,
        inner: MockClient((req) async {
          if (req.url.path.endsWith('/auth/refresh')) {
            return _ok({
              'accessToken': 'NEW',
              'tokenType': 'Bearer',
              'expiresIn': 900,
              'refreshToken': 'R2',
              'refreshExpiresIn': 604800,
            });
          }
          if (req.headers['Authorization'] == 'Bearer NEW') {
            retriedQuery = req.url.query;
            return _ok([]);
          }
          return _err(401, 'TOKEN_EXPIRED', 'expiré');
        }),
      );
      await client.get<List<dynamic>>('/notifications', (j) => [],
          query: {'recipientId': '42'});
      expect(retriedQuery, contains('recipientId=42'));
    });
  });
}
