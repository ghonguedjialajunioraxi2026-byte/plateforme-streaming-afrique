import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/user.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth/auth_controller.dart';
import '../social/social_repository.dart';

/// Profil : avatar, pseudo, bio, compteurs + édition (si API) + logout.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late Future<UserProfile> _future;

  @override
  void initState() {
    super.initState();
    final fallbackName =
        context.read<AuthController>().user?.displayName;
    _future = context.read<ProfileRepository>().me().catchError(
        (_) => UserProfile(displayName: fallbackName));
  }

  Future<void> _logout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Déconnexion'),
        content: const Text('Se déconnecter de FLICK ?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Annuler')),
          TextButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Déconnexion')),
        ],
      ),
    );
    if (confirm == true && mounted) {
      await context.read<AuthController>().logout();
      if (mounted) Navigator.of(context).pushNamedAndRemoveUntil('/login', (_) => false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthController>();
    return ListView(
      padding: const EdgeInsets.all(FlickSpacing.md),
      children: [
        FutureBuilder<UserProfile>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const FlickLoading(label: 'Profil…');
            }
            final profile = snap.data ??
                UserProfile(displayName: auth.user?.displayName);
            return FlickCard(
              child: Row(
                children: [
                  FlickAvatar(
                      name: profile.displayName,
                      url: profile.avatarUrl,
                      radius: 32),
                  const SizedBox(width: FlickSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                            profile.displayName ??
                                auth.user?.displayName ??
                                'Utilisateur',
                            style: FlickTypography.title),
                        if (profile.username != null)
                          Text('@${profile.username}',
                              style: FlickTypography.caption.copyWith(
                                  color: FlickColors.muted)),
                        const SizedBox(height: 4),
                        Text(
                            '${profile.followers} abonnés · ${profile.following} suivis',
                            style: FlickTypography.caption.copyWith(
                                color: FlickColors.muted)),
                      ],
                    ),
                  ),
                ],
              ),
            );
          },
        ),
        if ((context
                    .watch<AuthController>()
                    .user
                    ?.displayName ??
                '') !=
            '') ...[
          const SizedBox(height: FlickSpacing.sm),
          FutureBuilder<UserProfile>(
            future: _future,
            builder: (context, snap) {
              final bio = snap.data?.bio;
              if (bio == null || bio.isEmpty) {
                return const SizedBox.shrink();
              }
              return FlickCard(child: Text(bio, style: FlickTypography.bodySmall));
            },
          ),
        ],
        const SizedBox(height: FlickSpacing.md),
        FlickCard(
          onTap: () => Navigator.of(context).pushNamed('/wallet'),
          child: const Row(
            children: [
              Icon(Icons.account_balance_wallet_outlined,
                  color: FlickColors.green),
              SizedBox(width: FlickSpacing.sm),
              Expanded(child: Text('Portefeuille')),
              Icon(Icons.chevron_right, color: FlickColors.muted),
            ],
          ),
        ),
        const SizedBox(height: FlickSpacing.sm),
        FlickCard(
          onTap: () => Navigator.of(context).pushNamed('/subscriptions'),
          child: const Row(
            children: [
              Icon(Icons.subscriptions_outlined, color: FlickColors.green),
              SizedBox(width: FlickSpacing.sm),
              Expanded(child: Text('Abonnements')),
              Icon(Icons.chevron_right, color: FlickColors.muted),
            ],
          ),
        ),
        const SizedBox(height: FlickSpacing.sm),
        FlickCard(
          onTap: () => Navigator.of(context).pushNamed('/settings'),
          child: const Row(
            children: [
              Icon(Icons.settings_outlined, color: FlickColors.green),
              SizedBox(width: FlickSpacing.sm),
              Expanded(child: Text('Paramètres')),
              Icon(Icons.chevron_right, color: FlickColors.muted),
            ],
          ),
        ),
        const SizedBox(height: FlickSpacing.lg),
        FlickButton(
            label: 'Déconnexion', secondary: true, onPressed: _logout),
      ],
    );
  }
}
