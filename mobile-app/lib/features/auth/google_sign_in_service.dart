import 'package:google_sign_in/google_sign_in.dart';

import '../../core/config/env.dart';

/// Échec du flux Google (hors annulation utilisateur).
class GoogleSignInException implements Exception {
  const GoogleSignInException(this.message);

  final String message;

  @override
  String toString() => 'GoogleSignInException: $message';
}

/// Obtention d'un id_token Google — seam testable (le plugin ne l'est pas).
///
/// Contrat : `signIn()` renvoie l'id_token OIDC, ou `null` si l'utilisateur
/// a annulé (silencieux côté UI). Toute autre anomalie lève
/// [GoogleSignInException] avec un message affichable.
abstract class GoogleIdTokenProvider {
  Future<String?> signIn();

  Future<void> signOut();
}

/// Implémentation réelle via le plugin `google_sign_in`.
///
/// Point de sécurité : `serverClientId` = Client ID OAuth **WEB** (public,
/// cf. [Env.googleServerClientId]). Le plugin demande alors un id_token
/// dont l'audience est ce client — exactement ce que le backend vérifie
/// (`GOOGLE_CLIENT_ID`). Sans cela, l'audience serait le client natif et
/// le backend rejetterait le token en 401.
class PluginGoogleIdTokenProvider implements GoogleIdTokenProvider {
  PluginGoogleIdTokenProvider({GoogleSignIn? signIn})
      : _signIn = signIn ??
            GoogleSignIn(
              serverClientId: Env.googleServerClientId.isEmpty
                  ? null
                  : Env.googleServerClientId,
              scopes: const ['email', 'profile'],
            );

  final GoogleSignIn _signIn;

  @override
  Future<String?> signIn() async {
    if (Env.googleServerClientId.isEmpty) {
      throw const GoogleSignInException(
          'Connexion Google non configurée sur cet appareil');
    }
    try {
      // Déconnexion préalable : force le sélecteur de compte (changement
      // de compte possible) au lieu de réutiliser le dernier silently.
      await _signIn.signOut();
      final account = await _signIn.signIn();
      if (account == null) return null; // annulé par l'utilisateur
      final auth = await account.authentication;
      final idToken = auth.idToken;
      if (idToken == null || idToken.isEmpty) {
        throw const GoogleSignInException(
            'Google n’a pas remis de jeton (réessayez)');
      }
      return idToken;
    } on GoogleSignInException {
      rethrow;
    } catch (_) {
      throw const GoogleSignInException(
          'Connexion Google impossible (réseau ou compte)');
    }
  }

  @override
  Future<void> signOut() async {
    try {
      await _signIn.signOut();
    } catch (_) {
      // Déconnexion FLICK déjà effectuée : ne jamais la bloquer.
    }
  }
}
