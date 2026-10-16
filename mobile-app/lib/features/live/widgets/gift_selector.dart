import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/wallet.dart';
import '../../../shared/widgets/widgets.dart';
import '../../wallet/wallet_repository.dart';

/// Sélecteur de cadeaux pendant un live.
///
/// ÉTAT HONNÊTE : aucun endpoint REST d'envoi de cadeau n'est exposé
/// par le backend (modèles GiftItem/GiftSend côté serveur uniquement,
/// agrégés au dashboard créateur). AUCUNE API parallèle n'est créée :
/// cette feuille affiche le solde réel + les packs (prix serveur) et
/// documente l'endpoint manquant : POST /api/streams/{id}/gifts
/// (catalogue : GET /api/gifts).
class GiftSelector extends StatefulWidget {
  const GiftSelector({super.key, required this.streamId});

  final int streamId;

  @override
  State<GiftSelector> createState() => _GiftSelectorState();
}

class _GiftSelectorState extends State<GiftSelector> {
  late Future<_GiftData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_GiftData> _load() async {
    final repo = context.read<WalletRepository>();
    final balance =
        await repo.balance().catchError((_) => WalletBalance(coins: 0));
    final packs =
        await repo.packages().catchError((_) => <CoinPackage>[]);
    return _GiftData(balance: balance, packs: packs);
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(FlickSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.card_giftcard, color: FlickColors.green),
                const SizedBox(width: FlickSpacing.sm),
                const Expanded(
                    child: Text('Envoyer un cadeau',
                        style: FlickTypography.title)),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.of(context).pop(),
                ),
              ],
            ),
            const SizedBox(height: FlickSpacing.sm),
            FutureBuilder<_GiftData>(
              future: _future,
              builder: (context, snap) {
                if (snap.connectionState == ConnectionState.waiting) {
                  return const FlickLoading(label: 'Portefeuille…');
                }
                if (snap.hasError) {
                  return FlickErrorState(
                    message: 'Portefeuille indisponible : ${snap.error}',
                    onRetry: () => setState(() => _future = _load()),
                  );
                }
                final data = snap.data!;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    FlickCard(
                      child: Row(
                        children: [
                          const Icon(Icons.monetization_on,
                              color: FlickColors.green),
                          const SizedBox(width: FlickSpacing.sm),
                          Text('${data.balance.coins} coins',
                              style: FlickTypography.title),
                          const Spacer(),
                          TextButton(
                            onPressed: () {
                              Navigator.of(context).pop();
                              Navigator.of(context).pushNamed('/wallet');
                            },
                            child: const Text('Recharger'),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: FlickSpacing.md),
                    const FlickEmptyState(
                      icon: Icons.card_giftcard_outlined,
                      message:
                          'L\u2019envoi de cadeaux sera activé avec l\u2019endpoint backend dédié (POST /api/streams/{id}/gifts). En attendant, rechargez vos coins : le serveur reste la seule source de vérité des prix et des soldes.',
                    ),
                    const SizedBox(height: FlickSpacing.sm),
                    const Text(
                      'Les packs de coins sont achetés via le portefeuille (prix fournis par le serveur).',
                      style: FlickTypography.caption,
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _GiftData {
  _GiftData({required this.balance, required this.packs});

  final WalletBalance balance;
  final List<CoinPackage> packs;
}
