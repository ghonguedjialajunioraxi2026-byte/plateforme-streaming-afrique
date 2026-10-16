import 'package:flutter/material.dart';

import 'app.dart';
import 'core/network/api_client.dart';
import 'core/storage/app_prefs.dart';
import 'core/storage/token_store.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/auth_repository.dart';

/// Entrée FLICK mobile — assemble les dépendances (backend = seule source).
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final tokens = SecureTokenStore();
  final prefs = await AppPrefs.load();
  late final AuthController auth;
  final api = ApiClient(
    tokens: tokens,
    onSessionExpired: () => auth.forceLogout(),
  );
  auth = AuthController(AuthRepository(api));
  runApp(FlickApp(api: api, tokens: tokens, prefs: prefs, auth: auth));
}
