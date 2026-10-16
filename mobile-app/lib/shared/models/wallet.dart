/// Wallet — solde servi par GET /api/wallet/me (serveur = vérité).
class WalletBalance {
  WalletBalance({required this.coins, this.currency});

  final int coins;
  final String? currency;

  factory WalletBalance.fromJson(dynamic json) {
    final map = (json as Map<String, dynamic>?) ?? {};
    final v = map['coins'] ?? map['balance'] ?? 0;
    return WalletBalance(
      coins: v is int ? v : int.tryParse('$v') ?? 0,
      currency: map['currency']?.toString(),
    );
  }
}

/// Transaction wallet — GET /api/wallet/transactions.
class WalletTx {
  WalletTx({this.id, this.type, this.amount = 0, this.label, this.at});

  final dynamic id;
  final String? type;
  final int amount;
  final String? label;
  final String? at;

  factory WalletTx.fromJson(Map<String, dynamic> json) => WalletTx(
        id: json['id'],
        type: json['type']?.toString(),
        amount: json['amount'] is int
            ? json['amount'] as int
            : int.tryParse('${json['amount']}') ?? 0,
        label: (json['label'] ?? json['description'])?.toString(),
        at: (json['createdAt'] ?? json['at'])?.toString(),
      );
}

/// Pack de coins — GET /api/coin-packages (prix servi, jamais inventé).
class CoinPackage {
  CoinPackage(
      {required this.id, required this.coins, this.price, this.currency});

  final dynamic id;
  final int coins;
  final num? price;
  final String? currency;

  factory CoinPackage.fromJson(Map<String, dynamic> json) => CoinPackage(
        id: json['id'],
        coins: json['coins'] is int
            ? json['coins'] as int
            : int.tryParse('${json['coins']}') ?? 0,
        price: json['price'] is num ? json['price'] as num : null,
        currency: json['currency']?.toString(),
      );
}

/// Paiement — GET /api/payments/mine.
class PaymentItem {
  PaymentItem({this.id, this.status, this.amount, this.provider, this.at});

  final dynamic id;
  final String? status;
  final String? amount;
  final String? provider;
  final String? at;

  factory PaymentItem.fromJson(Map<String, dynamic> json) => PaymentItem(
        id: json['id'],
        status: json['status']?.toString(),
        amount: json['amount']?.toString(),
        provider:
            (json['provider'] ?? json['providerName'])?.toString(),
        at: (json['createdAt'] ?? json['at'])?.toString(),
      );
}

/// Fournisseur de paiement configuré — GET /api/payments/providers.
class PaymentProvider {
  PaymentProvider({required this.code, required this.name, this.countries});

  final String code;
  final String name;
  final List<String>? countries;

  factory PaymentProvider.fromJson(Map<String, dynamic> json) =>
      PaymentProvider(
        code: (json['code'] ?? json['id'] ?? '').toString(),
        name: (json['name'] ?? json['label'] ?? '').toString(),
        countries: (json['countries'] as List?)
            ?.map((e) => e.toString())
            .toList(),
      );
}
