import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/content.dart';
import '../../../shared/widgets/widgets.dart';
import '../auth/auth_controller.dart';
import '../wallet/wallet_repository.dart';

/// Abonnements : chaînes suivies (serveur) — plans/statuts servis,
/// jamais de prix final décidé côté mobile.
class SubscriptionsScreen extends StatefulWidget {
  const SubscriptionsScreen({super.key});

  @override
  State<SubscriptionsScreen> createState() => _SubscriptionsScreenState();
}

class _SubscriptionsScreenState extends State<SubscriptionsScreen> {
  late Future<List<Creator>> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<List<Creator>> _load() {
    final auth = context.read<AuthController>();
    if (!auth.isAuthenticated) return Future.value([]);
    return context
        .read<WalletRepository>()
        .following(auth.user!.id)
        .catchError((_) => <Creator>[]);
  }

  @override
  Widget build(BuildContext context) {
    final authed = context.watch<AuthController>().isAuthenticated;
    if (!authed) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(FlickSpacing.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const FlickEmptyState(
                  message: 'Connectez-vous pour voir vos abonnements'),
              const SizedBox(height: FlickSpacing.md),
              FlickButton(
                  label: 'Connexion',
                  onPressed: () =>
                      Navigator.of(context).pushNamed('/login')),
            ],
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () async => setState(() => _future = _load()),
      child: FutureBuilder<List<Creator>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Padding(
              padding: EdgeInsets.all(FlickSpacing.md),
              child: FlickSkeletonList(),
            );
          }
          if (snap.hasError) {
            return FlickErrorState(
              message: 'Abonnements indisponibles : ${snap.error}',
              onRetry: () => setState(() => _future = _load()),
            );
          }
          final items = snap.data!;
          if (items.isEmpty) {
            return const FlickEmptyState(
                icon: Icons.subscriptions_outlined,
                message: 'Aucun abonnement actif');
          }
          return ListView.separated(
            padding: const EdgeInsets.all(FlickSpacing.md),
            itemCount: items.length,
            separatorBuilder: (_, _) =>
                const SizedBox(height: FlickSpacing.sm),
            itemBuilder: (_, i) {
              final c = items[i];
              return FlickCard(
                onTap: () => Navigator.of(context)
                    .pushNamed('/channel', arguments: c.slug),
                child: Row(
                  children: [
                    FlickAvatar(
                        name: c.name,
                        url: c.avatarUrl,
                        showDot: c.isLive),
                    const SizedBox(width: FlickSpacing.sm),
                    Expanded(
                        child: Text(c.name,
                            style: FlickTypography.bodySmall.copyWith(
                                fontWeight: FontWeight.w600))),
                    const FlickBadge(label: 'Actif'),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}
