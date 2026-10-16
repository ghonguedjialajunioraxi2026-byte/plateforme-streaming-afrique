import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/config/env.dart';
import '../../../core/theme/flick_colors.dart';
import '../../../l10n/app_strings.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth_controller.dart';

/// Bouton « Continuer avec Google » — POST /api/auth/google (backend).
///
/// - [onAuthenticated] : navigation après succès (ex. `/home`) ;
/// - annulation native : silencieuse (aucun message) ;
/// - erreur (réseau, 401, 409 compte local, 503 non configuré) : snackbar
///   avec le message serveur quand il existe.
/// - sans `GOOGLE_SERVER_CLIENT_ID` (dart-define) : bouton désactivé avec
///   mention — jamais d'appel voué à l'échec.
class GoogleSignInButton extends StatelessWidget {
  const GoogleSignInButton({super.key, required this.onAuthenticated});

  final VoidCallback onAuthenticated;

  Future<void> _submit(BuildContext context) async {
    final ok = await context.read<AuthController>().loginWithGoogle();
    if (!context.mounted) return;
    if (ok) {
      onAuthenticated();
    } else {
      final error = context.read<AuthController>().error;
      if (error != null) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error)));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    const s = S('fr');
    final busy = context.watch<AuthController>().busy;
    final configured = Env.googleServerClientId.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            const Expanded(child: Divider()),
            Padding(
              padding: const EdgeInsets.symmetric(
                  horizontal: FlickSpacing.md),
              child: Text(s.orDivider,
                  style: const TextStyle(color: FlickColors.muted)),
            ),
            const Expanded(child: Divider()),
          ],
        ),
        const SizedBox(height: FlickSpacing.md),
        FlickButton(
          label: s.continueWithGoogle,
          secondary: true,
          icon: Icons.g_mobiledata,
          loading: busy,
          onPressed:
              !busy && configured ? () => _submit(context) : null,
        ),
        if (!configured) ...[
          const SizedBox(height: FlickSpacing.sm),
          Text(
            s.googleUnavailable,
            textAlign: TextAlign.center,
            style:
                const TextStyle(color: FlickColors.muted, fontSize: 12),
          ),
        ],
      ],
    );
  }
}
