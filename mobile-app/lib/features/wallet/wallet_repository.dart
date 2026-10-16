import '../../core/constants/api_paths.dart';
import '../../core/network/api_client.dart';
import '../../shared/models/content.dart';
import '../../shared/models/wallet.dart';

/// Wallet + paiements + abonnements (lecture) — serveur = vérité.
/// Les prix ne sont JAMAIS inventés côté mobile.
class WalletRepository {
  WalletRepository(this._api);

  final ApiClient _api;

  Future<WalletBalance> balance() async {
    final json = await _api.get<dynamic>(
        ApiPaths.walletMe, (j) => j);
    return WalletBalance.fromJson(json);
  }

  Future<List<WalletTx>> transactions() async {
    try {
      final list = await _api.get<List<WalletTx>>(
        ApiPaths.walletTransactions,
        (j) {
          final raw = j is List
              ? j
              : ((j as Map<String, dynamic>?)?['content'] ?? []);
          return (raw as List)
              .whereType<Map<String, dynamic>>()
              .map(WalletTx.fromJson)
              .toList();
        },
      );
      return list;
    } catch (_) {
      return [];
    }
  }

  Future<List<CoinPackage>> packages() async {
    try {
      final list = await _api.get<List<CoinPackage>>(
        ApiPaths.coinPackages,
        (j) {
          final raw = j is List
              ? j
              : ((j as Map<String, dynamic>?)?['content'] ?? []);
          return (raw as List)
              .whereType<Map<String, dynamic>>()
              .map(CoinPackage.fromJson)
              .toList();
        },
        auth: false,
      );
      return list;
    } catch (_) {
      return [];
    }
  }

  Future<List<PaymentItem>> myPayments() async {
    try {
      final list = await _api.get<List<PaymentItem>>(
        ApiPaths.myPayments,
        (j) {
          final raw = j is List
              ? j
              : ((j as Map<String, dynamic>?)?['content'] ?? []);
          return (raw as List)
              .whereType<Map<String, dynamic>>()
              .map(PaymentItem.fromJson)
              .toList();
        },
      );
      return list;
    } catch (_) {
      return [];
    }
  }

  Future<List<PaymentProvider>> providers() async {
    try {
      final list = await _api.get<List<PaymentProvider>>(
        ApiPaths.paymentProviders,
        (j) {
          final raw = j is List
              ? j
              : ((j as Map<String, dynamic>?)?['items'] ?? []);
          return (raw as List)
              .whereType<Map<String, dynamic>>()
              .map(PaymentProvider.fromJson)
              .toList();
        },
        auth: false,
      );
      return list;
    } catch (_) {
      return [];
    }
  }

  /// Chaînes suivies — GET /api/follows/{id}/following (abonnements vus
  /// comme suivis ; les plans/ statuts viennent du serveur).
  Future<List<Creator>> following(int followerId) async {
    try {
      final list = await _api.get<List<Creator>>(
        ApiPaths.followingOf(followerId),
        (j) {
          final raw = j is List
              ? j
              : ((j as Map<String, dynamic>?)?['content'] ?? []);
          return (raw as List)
              .whereType<Map<String, dynamic>>()
              .map(Creator.fromJson)
              .toList();
        },
      );
      return list;
    } catch (_) {
      return [];
    }
  }
}
