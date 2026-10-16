import '../../core/constants/api_paths.dart';
import '../../core/network/api_client.dart';
import '../../shared/models/user.dart';

/// Authentification — utilise UNIQUEMENT les endpoints backend existants.
/// Le rôle est forcé USER côté serveur : jamais envoyé par le client.
class AuthRepository {
  AuthRepository(this._api);

  final ApiClient _api;

  static Map<String, dynamic> _map(dynamic json) =>
      (json as Map<String, dynamic>?) ?? {};

  Future<FlickUser> register({
    required String username,
    required String email,
    required String password,
  }) async {
    final json = await _api.post<Map<String, dynamic>>(
      ApiPaths.authRegister,
      _map,
      body: {
        'username': username,
        'email': email,
        'password': password,
        'confirmPassword': password,
      },
      auth: false,
    );
    return FlickUser.fromJson(json);
  }

  Future<FlickUser> login({
    required String email,
    required String password,
  }) async {
    final json = await _api.post<Map<String, dynamic>>(
      ApiPaths.authLogin,
      _map,
      body: {'email': email, 'password': password},
      auth: false,
    );
    await _storeSession(json);
    return FlickUser.fromJson(json);
  }

  /// Connexion Google : id_token OIDC vérifié CÔTÉ SERVEUR (signature,
  /// audience, expiration) → même contrat que /login (CAS 1/2).
  /// 409 = email déjà pris par un compte LOCAL (aucune fusion auto :
  /// se connecter par mot de passe puis lier depuis les paramètres).
  /// 503 = GOOGLE_CLIENT_ID absent côté backend (fonction désactivée).
  Future<FlickUser> loginWithGoogle({required String idToken}) async {
    final json = await _api.post<Map<String, dynamic>>(
      ApiPaths.authGoogle,
      _map,
      body: {'idToken': idToken},
      auth: false,
    );
    await _storeSession(json);
    return FlickUser.fromJson(json);
  }

  Future<void> _storeSession(Map<String, dynamic> json) async {
    _api.tokens.accessToken = json['accessToken']?.toString();
    await _api.tokens.writeRefresh(json['refreshToken']?.toString());
  }

  Future<void> logout() async {
    final refresh = await _api.tokens.readRefresh();
    try {
      if (refresh != null && refresh.isNotEmpty) {
        await _api.post<dynamic>(ApiPaths.authLogout, (_) => null,
            body: {'refreshToken': refresh});
      }
    } catch (_) {
      // Déconnexion locale même si le serveur est injoignable.
    }
    _api.tokens.accessToken = null;
    await _api.tokens.writeRefresh(null);
  }

  Future<FlickUser> me() async {
    final json = await _api.get<Map<String, dynamic>>(ApiPaths.authMe, _map);
    return FlickUser.fromJson(json);
  }
}
