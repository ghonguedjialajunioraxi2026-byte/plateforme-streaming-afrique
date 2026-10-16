import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/theme/flick_colors.dart';
import '../../../core/theme/flick_theme.dart';
import '../../../shared/models/wallet.dart';
import '../../../shared/widgets/widgets.dart';
import 'wallet_repository.dart';

/// Portefeuille : solde réel, historique, packs (prix serveur).
/// Le mobile ne modifie JAMAIS le solde directement.
class WalletScreen extends StatefulWidget {
  const WalletScreen({super.key});

  @override
  State<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends State<WalletScreen> {
  late Future<_WalletData> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_WalletData> _load() async {
    final repo = context.read<WalletRepository>();
    final balance =
        await repo.balance().catchError((_) => WalletBalance(coins: 0));
    final txs = await repo.transactions().catchError((_) => <WalletTx>[]);
    final packs = await repo.packages().catchError((_) => <CoinPackage>[]);
    final providers =
        await repo.providers().catchError((_) => <PaymentProvider>[]);
    return _WalletData(
        balance: balance, txs: txs, packs: packs, providers: providers);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Portefeuille')),
      body: RefreshIndicator(
        onRefresh: () async => setState(() => _future = _load()),
        child: FutureBuilder<_WalletData>(
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
            return ListView(
              padding: const EdgeInsets.all(FlickSpacing.md),
              children: [
                FlickCard(
                  child: Column(
                    children: [
                      const Text('Solde',
                          style: FlickTypography.caption),
                      Text('${data.balance.coins} coins',
                          style: FlickTypography.display.copyWith(
                              color: FlickColors.green)),
                      if (data.balance.currency != null)
                        Text(data.balance.currency!,
                            style: FlickTypography.caption.copyWith(
                                color: FlickColors.muted)),
                    ],
                  ),
                ),
                const SizedBox(height: FlickSpacing.lg),
                const FlickSectionHeader(title: 'Recharger (prix serveur)'),
                const SizedBox(height: FlickSpacing.sm),
                if (data.packs.isEmpty)
                  const FlickEmptyState(
                      message: 'Aucun pack disponible')
                else
                  ...data.packs.map((p) => Padding(
                        padding: const EdgeInsets.only(
                            bottom: FlickSpacing.sm),
                        child: FlickCard(
                          child: Row(
                            children: [
                              const Icon(Icons.monetization_on,
                                  color: FlickColors.green),
                              const SizedBox(width: FlickSpacing.sm),
                              Expanded(
                                  child: Text('${p.coins} coins',
                                      style: FlickTypography.bodySmall
                                          .copyWith(
                                              fontWeight:
                                                  FontWeight.w600))),
                              if (p.price != null)
                                Text(
                                    '${p.price} ${p.currency ?? ''}',
                                    style: FlickTypography.bodySmall),
                            ],
                          ),
                        ),
                      )),
                if (data.providers.isNotEmpty) ...[
                  const SizedBox(height: FlickSpacing.md),
                  Text(
                      'Fournisseurs : ${data.providers.map((p) => p.name).join(', ')}',
                      style: FlickTypography.caption
                          .copyWith(color: FlickColors.muted)),
                ],
                const SizedBox(height: FlickSpacing.lg),
                const FlickSectionHeader(title: 'Historique'),
                const SizedBox(height: FlickSpacing.sm),
                if (data.txs.isEmpty)
                  const FlickEmptyState(
                      message: 'Aucune transaction')
                else
                  ...data.txs.take(20).map((t) => Padding(
                        padding: const EdgeInsets.only(
                            bottom: FlickSpacing.sm),
                        child: FlickCard(
                          child: Row(
                            children: [
                              Expanded(
                                child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.start,
                                  children: [
                                    Text(t.label ?? t.type ?? 'Transaction',
                                        style: FlickTypography.bodySmall),
                                    if (t.at != null)
                                      Text(t.at!,
                                          style: FlickTypography.caption
                                              .copyWith(
                                                  color:
                                                      FlickColors.muted)),
                                  ],
                                ),
                              ),
                              Text('${t.amount > 0 ? '+' : ''}${t.amount}',
                                  style: FlickTypography.bodySmall.copyWith(
                                      color: t.amount >= 0
                                          ? FlickColors.green
                                          : FlickColors.danger,
                                      fontWeight: FontWeight.w700)),
                            ],
                          ),
                        ),
                      )),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _WalletData {
  _WalletData(
      {required this.balance,
      required this.txs,
      required this.packs,
      required this.providers});

  final WalletBalance balance;
  final List<WalletTx> txs;
  final List<CoinPackage> packs;
  final List<PaymentProvider> providers;
}
