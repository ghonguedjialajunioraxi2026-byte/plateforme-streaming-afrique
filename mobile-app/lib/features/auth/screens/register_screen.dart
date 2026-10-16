import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/utils/validators.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth_controller.dart';
import '../widgets/google_sign_in_button.dart';

/// Inscription — POST /api/auth/register (rôle forcé USER côté serveur)
/// ou connexion Google (le backend crée le compte si besoin).
class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _username.dispose();
    _email.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_form.currentState?.validate() ?? false)) return;
    final ok = await context.read<AuthController>().register(
          _username.text.trim(),
          _email.text.trim(),
          _password.text,
        );
    if (!mounted) return;
    if (ok) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Compte créé ! Connectez-vous.')));
      Navigator.of(context).pushReplacementNamed('/login');
    } else {
      final error = context.read<AuthController>().error ?? 'Erreur';
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final busy = context.watch<AuthController>().busy;
    return Scaffold(
      appBar: AppBar(title: const Text('Inscription')),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(FlickSpacing.xl),
            child: Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: FlickLogo(height: 56)),
                  const SizedBox(height: FlickSpacing.lg),
                  FlickTextField(
                    controller: _username,
                    label: 'Pseudonyme',
                    prefixIcon: Icons.person_outline,
                    validator: validateUsername,
                  ),
                  const SizedBox(height: FlickSpacing.md),
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
                    label: 'Mot de passe (8+, lettre + chiffre)',
                    obscure: _obscure,
                    prefixIcon: Icons.lock_outline,
                    validator: validatePassword,
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
                      label: 'Créer mon compte',
                      loading: busy,
                      onPressed: _submit),
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
