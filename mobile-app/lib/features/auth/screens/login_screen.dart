import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/utils/validators.dart';
import '../../../l10n/app_strings.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth_controller.dart';
import '../widgets/google_sign_in_button.dart';

/// Connexion — POST /api/auth/login ou Google (backend existant).
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_form.currentState?.validate() ?? false)) return;
    final ok = await context.read<AuthController>().login(
          _email.text.trim(),
          _password.text,
        );
    if (!mounted) return;
    if (ok) {
      Navigator.of(context).pushReplacementNamed('/home');
    } else {
      final error = context.read<AuthController>().error ?? 'Erreur';
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) {
    const s = S('fr');
    final busy = context.watch<AuthController>().busy;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(FlickSpacing.xl),
            child: Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: FlickLogo(height: 64)),
                  const SizedBox(height: FlickSpacing.xl),
                  const Text('Connexion',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                          fontSize: 24, fontWeight: FontWeight.w800)),
                  const SizedBox(height: FlickSpacing.sm),
                  const Text('Connectez-vous à votre compte FLICK',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: FlickColors.muted)),
                  const SizedBox(height: FlickSpacing.xl),
                  FlickTextField(
                    controller: _email,
                    label: 'Email',
                    keyboardType: TextInputType.emailAddress,
                    prefixIcon: Icons.alternate_email,
                    validator: validateEmail,
                  ),
                  const SizedBox(height: FlickSpacing.md),
                  FlickTextField(
                    controller: _password,
                    label: 'Mot de passe',
                    obscure: _obscure,
                    prefixIcon: Icons.lock_outline,
                    validator: (v) => validateRequired(v, 'Le mot de passe'),
                    onSubmitted: (_) => _submit(),
                    suffixIcon: IconButton(
                      icon: Icon(_obscure
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined),
                      onPressed: () =>
                          setState(() => _obscure = !_obscure),
                    ),
                  ),
                  const SizedBox(height: FlickSpacing.lg),
                  FlickButton(
                      label: s.login, loading: busy, onPressed: _submit),
                  const SizedBox(height: FlickSpacing.sm),
                  TextButton(
                    onPressed: () =>
                        Navigator.of(context).pushNamed('/register'),
                    child: const Text('Pas de compte ? Créer un compte'),
                  ),
                  const SizedBox(height: FlickSpacing.md),
                  GoogleSignInButton(
                    onAuthenticated: () => Navigator.of(context)
                        .pushReplacementNamed('/home'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
