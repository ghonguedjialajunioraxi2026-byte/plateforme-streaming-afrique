import 'package:flutter/foundation.dart';

import '../../core/network/api_exception.dart';
import 'auth_repository.dart';
import 'google_sign_in_service.dart';
import '../../shared/models/user.dart';

/// Session FLICK — états : loading / authenticated / unauthenticated.
/// Refresh auto via ApiClient ; refresh impossible → logout propre.
/// Évite les boucles : un seul appel me() par restauration de session.
class AuthController extends ChangeNotifier {
  AuthController(this._repo, {GoogleIdTokenProvider? googleSignIn})
      : _googleSignIn = googleSignIn ?? PluginGoogleIdTokenProvider();

  final AuthRepository _repo;
  final GoogleIdTokenProvider _googleSignIn;

  bool _ready = false;
  bool _busy = false;
  FlickUser? _user;
  String? _error;

  bool get ready => _ready;
  bool get busy => _busy;
  bool get isAuthenticated => _user != null;
  FlickUser? get user => _user;
  String? get error => _error;

  /// Restauration au démarrage : me() (le refresh auto a lieu sur 401).
  Future<void> restore() async {
    if (_ready) return;
    try {
      _user = await _repo.me();
    } on ApiException {
      _user = null; // non connecté : état normal, pas d'erreur affichée
    } finally {
      _ready = true;
      notifyListeners();
    }
  }

  Future<bool> login(String email, String password) {
    return _guard(() async {
      _user = await _repo.login(email: email, password: password);
    });
  }

  /// Connexion Google : sélecteur de compte natif → id_token → backend.
  /// Annulation = retour silencieux (false sans erreur) ; 409/503 backend
  /// et échecs plugin remontent en [_error] affichable.
  Future<bool> loginWithGoogle() async {
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      final idToken = await _googleSignIn.signIn();
      if (idToken == null) return false; // annulé : silencieux
      _user = await _repo.loginWithGoogle(idToken: idToken);
      return true;
    } on ApiException catch (e) {
      _error = e.message;
      return false;
    } on GoogleSignInException catch (e) {
      _error = e.message;
      return false;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }

  Future<bool> register(String username, String email, String password) {
    return _guard(() async {
      await _repo.register(username: username, email: email, password: password);
      // Inscription ≠ session : l'utilisateur se connecte ensuite.
    });
  }

  Future<void> logout() async {
    _busy = true;
    notifyListeners();
    try {
      await _repo.logout();
    } finally {
      _user = null;
      _busy = false;
      notifyListeners();
    }
  }

  /// Appelé par ApiClient quand le refresh est impossible.
  void forceLogout() {
    _user = null;
    notifyListeners();
  }

  Future<bool> _guard(Future<void> Function() action) async {
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      await action();
      return true;
    } on ApiException catch (e) {
      _error = e.message;
      return false;
    } finally {
      _busy = false;
      notifyListeners();
    }
  }
}
