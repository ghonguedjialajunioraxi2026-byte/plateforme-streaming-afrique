import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth_controller.dart';

/// Splash premium : fond sombre + logo FLICK, puis routage session.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    _boot();
  }

  Future<void> _boot() async {
    final auth = context.read<AuthController>();
    await auth.restore();
    if (!mounted) return;
    Navigator.of(context).pushReplacementNamed(
      auth.isAuthenticated ? '/home' : '/login',
    );
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: FlickColors.black,
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            FlickLogo(height: 96),
            SizedBox(height: FlickSpacing.xl),
            SizedBox(
              width: 28, height: 28,
              child: CircularProgressIndicator(strokeWidth: 3),
            ),
          ],
        ),
      ),
    );
  }
}
