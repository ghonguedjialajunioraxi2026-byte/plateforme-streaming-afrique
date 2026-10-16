import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../config/env.dart';
import '../constants/api_paths.dart';
import 'api_exception.dart';

/// Stockage minimal requis par le client (tokens). Implémenté par
/// SecureTokenStore — jamais de mot de passe persisté.
abstract class TokenStore {
  String? get accessToken;
  set accessToken(String? value);
  Future<String?> readRefresh();
  Future<void> writeRefresh(String? value);
}

/// Client HTTP centralisé — SEUL point d'accès au backend.
///
/// - déballe l'enveloppe {success, data} ;
/// - Authorization: Bearer (access en mémoire via TokenStore) ;
/// - 401 → UN SEUL refresh en vol (single-flight), retry unique,
///   jamais sur /auth/login ni /auth/refresh (pas de boucle infinie) ;
/// - refresh impossible → [onSessionExpired] (déconnexion propre).
class ApiClient {
  ApiClient({
    required this.tokens,
    http.Client? inner,
    this.onSessionExpired,
    String? baseUrl,
  })  : _inner = inner ?? http.Client(),
        baseUrl = (baseUrl ?? Env.apiBaseUrl).replaceAll(RegExp(r'/$'), '');

  final TokenStore tokens;
  final http.Client _inner;
  void Function()? onSessionExpired;
  final String baseUrl;

  Future<void>? _refreshFlight;

  static const Duration timeout = Duration(seconds: 20);

  Map<String, String> _headers({bool auth = true}) {
    final headers = {'Content-Type': 'application/json'};
    final token = tokens.accessToken;
    if (auth && token != null && token.isNotEmpty) {
      headers['Authorization'] = 'Bearer $token';
    }
    return headers;
  }

  Future<T> get<T>(String path, T Function(dynamic json) decode,
      {Map<String, String>? query, bool auth = true}) async {
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: query);
    return _send<T>(() => _inner.get(uri, headers: _headers(auth: auth)), decode,
        path: path, method: 'GET', uri: uri, auth: auth);
  }

  Future<T> post<T>(String path, T Function(dynamic json) decode,
      {Object? body, bool auth = true}) async {
    return _send<T>(
      () => _inner.post(Uri.parse('$baseUrl$path'),
          headers: _headers(auth: auth),
          body: body == null ? null : jsonEncode(body)),
      decode,
      path: path,
      method: 'POST',
      uri: Uri.parse('$baseUrl$path'),
      auth: auth,
      body: body,
    );
  }

  Future<T> put<T>(String path, T Function(dynamic json) decode,
      {Object? body}) async {
    return _send<T>(
      () => _inner.put(Uri.parse('$baseUrl$path'),
          headers: _headers(), body: body == null ? null : jsonEncode(body)),
      decode,
      path: path,
      method: 'PUT',
      uri: Uri.parse('$baseUrl$path'),
      body: body,
    );
  }

  Future<T> patch<T>(String path, T Function(dynamic json) decode,
      {Object? body}) async {
    return _send<T>(
      () => _inner.patch(Uri.parse('$baseUrl$path'),
          headers: _headers(), body: body == null ? null : jsonEncode(body)),
      decode,
      path: path,
      method: 'PATCH',
      uri: Uri.parse('$baseUrl$path'),
      body: body,
    );
  }

  Future<T> delete<T>(String path, T Function(dynamic json) decode,
      {Map<String, String>? query}) async {
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: query);
    return _send<T>(
        () => _inner.delete(uri, headers: _headers()), decode,
        path: path, method: 'DELETE', uri: uri);
  }

  static bool _isAuthRoute(String path) =>
      path == ApiPaths.authLogin || path == ApiPaths.authRefresh;

  Future<T> _send<T>(Future<http.Response> Function() call,
      T Function(dynamic json) decode,
      {required String path,
      required String method,
      required Uri uri,
      bool auth = true,
      Object? body,
      bool retried = false}) async {
    late http.Response res;
    try {
      res = await call().timeout(timeout);
    } on TimeoutException {
      throw ApiException.timeout();
    } catch (_) {
      throw ApiException.network();
    }

    if (res.statusCode == 401 &&
        auth &&
        !retried &&
        !_isAuthRoute(path) &&
        tokens.accessToken != null) {
      final renewed = await _singleFlightRefresh();
      if (renewed) {
        // Retry unique : rejoue la MÊME méthode sur la MÊME URI
        // (query params conservés) avec le nouvel access token.
        Future<http.Response> retry() {
          final headers = _headers();
          final payload = body == null ? null : jsonEncode(body);
          switch (method) {
            case 'POST':
              return _inner.post(uri, headers: headers, body: payload);
            case 'PUT':
              return _inner.put(uri, headers: headers, body: payload);
            case 'PATCH':
              return _inner.patch(uri, headers: headers, body: payload);
            case 'DELETE':
              return _inner.delete(uri, headers: headers);
            case 'GET':
            default:
              return _inner.get(uri, headers: headers);
          }
        }

        return _send<T>(retry, decode,
            path: path, method: method, uri: uri, auth: auth, body: body, retried: true);
      }
      onSessionExpired?.call();
    }

    if (res.statusCode < 200 || res.statusCode >= 300) {
      throw _parseError(res);
    }
    if (res.statusCode == 204 || res.body.isEmpty) {
      return decode(null);
    }
    final json = jsonDecode(res.body);
    if (json is Map<String, dynamic> && json.containsKey('success')) {
      if (json['success'] == true) return decode(json['data']);
      throw ApiException.fromJson(res.statusCode, json);
    }
    return decode(json);
  }

  ApiException _parseError(http.Response res) {
    try {
      final json = jsonDecode(res.body);
      if (json is Map<String, dynamic>) {
        return ApiException.fromJson(res.statusCode, json);
      }
    } catch (_) {}
    return ApiException(res.statusCode, 'Erreur ${res.statusCode}');
  }

  /// Refresh single-flight : les 401 concurrents partagent la même promesse.
  Future<bool> _singleFlightRefresh() {
    final flight = _refreshFlight;
    if (flight != null) return flight.then((_) => tokens.accessToken != null);
    final future = _doRefresh();
    _refreshFlight = future;
    future.whenComplete(() => _refreshFlight = null);
    return future;
  }

  Future<bool> _doRefresh() async {
    final refresh = await tokens.readRefresh();
    if (refresh == null || refresh.isEmpty) return false;
    try {
      final res = await _inner
          .post(Uri.parse('$baseUrl${ApiPaths.authRefresh}'),
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({'refreshToken': refresh}))
          .timeout(timeout);
      if (res.statusCode < 200 || res.statusCode >= 300) {
        await tokens.writeRefresh(null);
        tokens.accessToken = null;
        return false;
      }
      final json = jsonDecode(res.body) as Map<String, dynamic>;
      final data = (json['data'] ?? json) as Map<String, dynamic>;
      tokens.accessToken = data['accessToken']?.toString();
      final next = data['refreshToken']?.toString();
      await tokens.writeRefresh(next); // rotation systématique côté serveur
      return tokens.accessToken != null;
    } catch (_) {
      return false;
    }
  }

  void close() => _inner.close();
}
