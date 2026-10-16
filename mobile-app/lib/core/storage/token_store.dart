import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../network/api_client.dart';

/// Stockage sécurisé des tokens (Keystore/Keychain).
///
/// - access token : mémoire UNIQUEMENT (jamais persisté) ;
/// - refresh token : secure storage (clé unique) ;
/// - JAMAIS de mot de passe stocké.
class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const String refreshKey = 'flick_refresh_token';

  @override
  String? accessToken;

  @override
  Future<String?> readRefresh() => _storage.read(key: refreshKey);

  @override
  Future<void> writeRefresh(String? value) {
    if (value == null) return _storage.delete(key: refreshKey);
    return _storage.write(key: refreshKey, value: value);
  }

  Future<void> clear() async {
    accessToken = null;
    await _storage.delete(key: refreshKey);
  }
}
